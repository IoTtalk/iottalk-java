package iottalk;

import java.lang.Thread;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.IllegalArgumentException;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InterruptedIOException;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class DAI extends Thread{
    private Logger logger = Logger.getLogger("DAI");
    
    private boolean aliveFlag;
    private Object sa;
    private DAN dan;
    
    private String csmEndpoint;
    private String[] acceptProtos = {"mqtt"};
    private String dName = null;
    private String deviceModel;
    private DeviceFeature[] dfList;
    private String deviceAddr = null;
    private boolean persistentBinding = false;
    private String userName = null;
    private double pushInterval;
    private Map<String, String> intervalMap;
    
    private Method onRegisterMethod;
    private Method onDeregisterMethod;
    private Method onConnectMethod;
    private Method onDisconnectMethod;
    
    private Map<String, Timer> pushDataTimerMap;
    private Map<String, DeviceFeature> dfMap;
    
    public DAI(Object _sa){
        sa = _sa;
        pushDataTimerMap = new HashMap<>();
        dfMap = new HashMap<>();
        //Set log format
        System.setProperty("java.util.logging.SimpleFormatter.format",
              "[%1$tT] [%4$s] %5$s %n");
    }
    
    public void terminate(){
        aliveFlag = false;  //set aliveFlag to false, this while break while loop
    }
    
    //Parse fields(variables) and methods from sa
    private void getVariables() throws IllegalAccessException{
        Class saClass = sa.getClass();
        Field[] fields = saClass.getDeclaredFields();
        Method[] methods = saClass.getDeclaredMethods();
        
        for (int i = 0; i < fields.length; i++) {
            String vName = fields[i].getName();
            Class cType = fields[i].getType();
            
            //get variable by name
            if (vName.equals("api_url")){
                csmEndpoint = (String)(fields[i].get(sa));
            }
            else if (vName.equals("device_name")){
                dName = (String)(fields[i].get(sa));
            }
            else if (vName.equals("device_model")){
                deviceModel = (String)(fields[i].get(sa));
            }
            else if (vName.equals("device_addr")){
                deviceAddr = (String)(fields[i].get(sa));
            }
            else if (vName.equals("persistent_binding")){
                persistentBinding = (boolean)(fields[i].get(sa));
            }
            else if (vName.equals("username")){
                userName = (String)(fields[i].get(sa));
            }
            else if (vName.equals("push_interval")){
                pushInterval = (double)(fields[i].get(sa));
            }
            else if (vName.equals("interval")){
                intervalMap = (Map<String, String>)(fields[i].get(sa));
            }
            //collect DeviceFeatures
            else if (cType == DeviceFeature.class){
                DeviceFeature dft = (DeviceFeature)(fields[i].get(sa));
                dfMap.put(dft.getDFName(), dft);
            }
        }
        //change Map to class array
        dfList = dfMap.values().toArray(new DeviceFeature[0]);
        
        //get methods by name
        for (int i = 0; i < methods.length; i++){
            String mName = methods[i].getName();
            if (mName.equals("on_register")){
                onRegisterMethod = methods[i];
            }
            else if (mName.equals("on_deregister")){
                onDeregisterMethod = methods[i];
            }
            else if (mName.equals("on_connect")){
                onConnectMethod = methods[i];
            }
            else if (mName.equals("on_disconnect")){
                onDisconnectMethod = methods[i];
            }
        }
    }
    
    private boolean _on_signal(String command, String df){
        logger.info("Receive signal: "+DAIColor.wrap(DANColor.dataString, command)+", "+df+".");
        if (command.equals("CONNECT")){
            if (pushDataTimerMap.containsKey(df)){
                return true;  //already connect
            }
            DeviceFeature dft = dfMap.get(df);
            if (dft.isIDF()){
                //count timer interval time
                double ti = pushInterval;
                if (intervalMap.containsKey(df)){
                    String tt = intervalMap.get(df);
                    ti *= Double.parseDouble(tt);
                }
                ti *= 1000;
                
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                  @Override
                  public void run() {
                      try{
                          JSONArray pushDataJSONArray = dft.publishData();
                          dan.push(df, pushDataJSONArray);
                      } catch(MqttException me){
                          me.printStackTrace();
                      } catch(JSONException je){
                          je.printStackTrace();
                      } catch(RegistrationError re){
                          re.printStackTrace();
                      }
                  }
                }, 0, (int)ti);
                //wait 0 ms before doing the action and do it every (ti) ms 
                pushDataTimerMap.put(df, timer);
            }
        }
        else if (command.equals("DISCONNECT")){
            DeviceFeature dft = dfMap.get(df);
            if (dft.isIDF()){
                Timer timer = pushDataTimerMap.get(df);
                timer.cancel();
                pushDataTimerMap.remove(df);
            }
            
        }
        else if (command.equals("SUSPEND")); //Not use
        else if (command.equals("RESUME")); //Not use
        return true;
    }
    
    //clean all timer
    //invoke when process exiting
    private void timerClear(){
        for (String dfn : pushDataTimerMap.keySet())  
        { 
            Timer timer = pushDataTimerMap.get(dfn); 
            timer.cancel();
        } 
    }
    
    //invoke custom define method
    private void invokeMethod(Method m){
        if (m == null){
            return;
        }
        try{
            m.invoke(sa);
        } catch(Exception e){
            e.printStackTrace();
        }
    }
    
    //main
    @Override
    public void run(){
        try{
            aliveFlag = true;
            getVariables();  //parse sa
            
            //change id(device_addr) class to UUID
            //and check id's format
            AppID appId;
            if (persistentBinding == false){
                if ((deviceAddr == null)){
                    appId = new AppID();
                }
                else{
                    appId = new AppID(deviceAddr, false);
                }
            }
            else{
                if (deviceAddr == null){
                    throw new IllegalArgumentException("In case of `persistent_binding` set to `True`, the `device_addr` should be set and fixed.");
                }
                else{
                    appId = new AppID(deviceAddr);
                }
            }

            JSONObject putBodyPorfile = new JSONObject();
            putBodyPorfile.put("model", deviceModel);
            putBodyPorfile.put("u_name", userName);
            
            //new dan
            dan = new DAN(csmEndpoint, acceptProtos, dfList, appId, dName, putBodyPorfile){
                @Override
                public boolean on_signal(String command, String df){
                    //return true;
                    return _on_signal(command, df);
                }
                @Override
                public void on_register(){
                    invokeMethod(onRegisterMethod);
                }
                @Override
                public void on_deregister(){
                    invokeMethod(onDeregisterMethod);
                }
                @Override
                public void on_connect(){
                    invokeMethod(onConnectMethod);
                }
                @Override
                public void on_disconnect(){
                    invokeMethod(onDisconnectMethod);
                }
            };
            
            dan.register();
            
            logger.info("Successfully connect to "+DAIColor.wrap(DAIColor.dataString, "Press Ctrl+C to exit DAI")+".");
            
            //busy wait
            //FIXME : use other impletation, this will block for a will
            while(true){
                java.util.concurrent.TimeUnit.SECONDS.sleep(1);
                if (aliveFlag == false){
                    break;
                }
            }
            
            //preparing for exiting
            timerClear();
            dan.disconnect();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
