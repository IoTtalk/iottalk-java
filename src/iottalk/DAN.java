package iottalk;

import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class DAN{
    private static Logger logger = null;
    static {
      System.setProperty("java.util.logging.SimpleFormatter.format",
              "[%1$tT] [%4$s] %5$s %n");
      logger = Logger.getLogger("DAN");
    }
    
    private String csmEndpoint;
    private String[] acceptProtos;
    private DeviceFeature[] dfList;
    private AppID appID;
    private String dName;
    private JSONObject registerProfile;
    private JSONObject registerBodyJson;
    
    private String mqttHost;
    private int mqttPort;
    private String rev;
    
    private ChannelPool iChans;
    private ChannelPool oChans;
    
    private MqttAsyncClient client;
    private boolean isReconnectFlag;
    private boolean isRegisterFlag;
    
    public class DANColor extends ColorBase{
        public String logger = "\033[1;35m";
    }
    
    public class ApplicationNotFoundError extends Exception{
        public ApplicationNotFoundError(String s) 
        { 
            super(s); 
        }
    }
    
    public class RegistrationError extends Exception{
        public RegistrationError(String s) 
        { 
            super(s); 
        }
    }
    
    public class AttributeNotFoundError extends Exception{
        public AttributeNotFoundError(String s) 
        { 
            super(s); 
        }
    }
    
    public static boolean _invalid_url(String url){
        if (url == null){
            return true;
        }
        if (url == ""){
            return true;
        }
        return false;
    }
    
    public DAN(){
        
    }
    
    public DAN(String _csmUrl, 
               String[] _acceptProtos, 
               DeviceFeature[] _dfList, 
               AppID _id, 
               String _name, 
               JSONObject _profile)
        throws JSONException, RegistrationError  {
        
        
        isRegisterFlag = false;
        csmEndpoint = _csmUrl;
        acceptProtos = _acceptProtos;
        dfList = _dfList;
        appID = _id;
        dName = _name;
        registerProfile = _profile;
        iChans = new ChannelPool();
        oChans = new ChannelPool();
        if (_invalid_url(csmEndpoint)){
            throw new RegistrationError("Invalid url: "+csmEndpoint.toString());
        }
        //create register json file
        getRegisterBodyJson();
    }
    
    private void getRegisterBodyJson() throws JSONException {
        ArrayList<Object> idfList = new ArrayList <Object>();
        ArrayList<Object> odfList = new ArrayList <Object>();

        for(int i=0; i<dfList.length; i++){
            DeviceFeature dft = dfList[i];
            if(dft.isIDF()){
                idfList.add(dft.getArrayList());
                iChans.addDF(dft);
            }
            if(dft.isODF()){
                odfList.add(dft.getArrayList());
                oChans.addDF(dft);
            }
        }
        if (idfList.size() == 0){
            idfList.add(null);
        }
        if (odfList.size() == 0){
            odfList.add(null);
        }
        registerBodyJson = new JSONObject();
        registerBodyJson.put("accept_protos", new JSONArray(acceptProtos));
        registerBodyJson.put("name", dName);
        registerBodyJson.put("idf_list", new JSONArray(idfList));
        registerBodyJson.put("odf_list", new JSONArray(odfList));
        registerBodyJson.put("profile", registerProfile);
    }
    
    /*
    Custom onRegister
    Can be overrided when init DAN
    */
    public void onRegister(){
        return;
    }

    public void register() 
        throws IOException, ProtocolException, MqttException, RegistrationError, Exception
    {
        if (client != null){
            throw new RegistrationError("Already registered");
        }
        try{   
            URL url = new URL(csmEndpoint+"/"+appID);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            OutputStream output = conn.getOutputStream();
            output.write(registerBodyJson.toString().getBytes());

            //get response
            int code = conn.getResponseCode();

            InputStream input;
            if(code>=400) {//error code
                input = new BufferedInputStream(conn.getErrorStream());
            }
            else {
                input = new BufferedInputStream(conn.getInputStream());
            }
            if(code!=200){
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                String texts = "";
                String readstring;
                while((readstring = reader.readLine())!=null){
                    texts = texts+readstring+"\n";
                }
                throw new RegistrationError(texts);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String responseString;
            responseString = reader.readLine();
            isRegisterFlag = true;

            JSONObject metadata = new JSONObject(responseString);

            mqttHost = metadata.getJSONObject("url").getString("host");
            mqttPort = metadata.getJSONObject("url").getInt("port");
            rev = metadata.getString("rev");
            iChans.set("ctrl", metadata.getJSONArray("ctrl_chans").getString(0));
            oChans.set("ctrl", metadata.getJSONArray("ctrl_chans").getString(1));

            reader.close();
            conn.disconnect();
            
            onRegister();
            connect();
        } catch(JSONException e){
            throw new RegistrationError("Invalid response from server");
        } catch(MalformedURLException e){
            throw new RegistrationError("ConnectionError");
        }
    }
    
    private void connect()
        throws JSONException, MqttException, Exception
    {
        String mqttEndpoint = "tcp://"+mqttHost+":"+mqttPort;
        client = new MqttAsyncClient(mqttEndpoint, "iottalk-py-"+appID, new MemoryPersistence());
        
        MqttConnectOptions options = new MqttConnectOptions();
        JSONObject setWillBody = new JSONObject();
        setWillBody.put("state", "offline");
        setWillBody.put("rev", rev);
        options.setWill(iChans.getTopic("ctrl"), setWillBody.toString().getBytes(), 2, true);
        
        //connect and wait
        IMqttToken token = client.connect(options);
        token.waitForCompletion();
        subCtrlChans();
    }
    
    /*
    Custom onConnect
    Can be overrided when init DAN
    */
    public void onConnect(){
        return;
    }
    
    private void subCtrlChans() throws Exception{
        IMqttToken token;
        if (isReconnectFlag == false){
            logger.info("Successfully connect to "+DANColor.wrap(DANColor.dataString, csmEndpoint)+".");
            logger.info("Device ID: "+DANColor.wrap(DANColor.dataString, appID.toString())+".");
            logger.info("Device name: "+DANColor.wrap(DANColor.dataString, dName)+".");
            try{
                token = client.subscribe(oChans.getTopic("ctrl"), 2, ctrlChansCB);
            }catch(MqttException e){
                throw new Exception("Subscribe to control channel failed");
            }
            
            // TODO : check if sub success
        }
        else{
            logger.info("Reconnect: "+DANColor.wrap(DANColor.dataString, dName)+".");
            JSONObject publishBody = new JSONObject();
            publishBody.put("state", "offline");
            publishBody.put("rev", rev);
            token = client.publish(iChans.getTopic("ctrl"), publishBody.toString().getBytes(), 2, true);
            token.waitForCompletion();
            
            //TODO : print log
        }
        
        JSONObject publishBody = new JSONObject();
        publishBody.put("state", "online");
        publishBody.put("rev", rev);
        token = client.publish(iChans.getTopic("ctrl"), publishBody.toString().getBytes(), 2, true);
        token.waitForCompletion();
        
        isReconnectFlag = true;
        onConnect(); //call custom onConnect
    }
    
    public boolean push(String idfName, JSONArray data)throws MqttException, RegistrationError{
        //check if client is connected
        if (client.isConnected() == false){
            throw new RegistrationError("Not registered");
        }
        
        String pubTopic = iChans.getTopic(idfName);
        if (pubTopic == null){
            return false;  //topic not found
        }
        
        if (data.length() == 1){
          Object data_0 = (Object)data.get(0);
          if (JSONObject.NULL.equals(data_0))
            return true;
        }
        
        IMqttToken token = client.publish(pubTopic, data.toString().getBytes(), 2, true);
        token.waitForCompletion();
        return true;
    }
    
    /*
    Custom onSignal
    Can be overrided when init DAN
    */
    //FIX ME return Array (true), (false, reson)
    public boolean onSignal(String command, String df){
        ArrayList<Object> r = new ArrayList<Object>();
        r.add(true);
        r.add("default");
        return true;
    }
    
    // Set control channel calback
    IMqttMessageListener ctrlChansCB = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message)
                throws JSONException, MqttException
            {
                //get mqtt message
                JSONObject messageJSON = new JSONObject(new String(message.getPayload()));
                String command = messageJSON.getString("command");
                boolean handlingResult = true;
                //record df's topic name to ChannelPool,
                //and subscribe odf's callback function
                if (command.equals("CONNECT")){
                    if (messageJSON.has("idf")){
                        String pubTopic = messageJSON.getString("topic");
                        String name = messageJSON.getString("idf");
                        iChans.set(name, pubTopic);
                        handlingResult = onSignal(command, name); //call custom onSignal
                    }
                    else if(messageJSON.has("odf")){
                        String subTopic = messageJSON.getString("topic");
                        String name = messageJSON.getString("odf");
                        oChans.set(name, subTopic);
                        DeviceFeature dft = oChans.getDFCbyName(name);
                        handlingResult = onSignal(command, name); //call custom onSignal
                        client.subscribe(subTopic, 0, dft.getCallBack());
                    }
                }
                //remove df's topic name from ChannelPool,
                //and subscribe odf's callback function
                else if(command.equals("DISCONNECT")){
                    if (messageJSON.has("idf")){
                        String name = messageJSON.getString("idf");
                        iChans.remove(name);
                        handlingResult = onSignal(command, name); //call custom onSignal
                    }
                    else if(messageJSON.has("odf")){
                        String name = messageJSON.getString("odf");
                        String subTopic = oChans.getTopic(name);
                        oChans.remove(name);
                        client.unsubscribe(subTopic);
                        handlingResult = onSignal(command, name); //call custom onSignal
                    }
                }
                JSONObject publishBody = new JSONObject();
                publishBody.put("msg_id", messageJSON.getString("msg_id"));
                if (handlingResult){
                    publishBody.put("state", "ok");
                }
                else{
                    publishBody.put("state", "error");
                    publishBody.put("state", "reason");
                }
                // FIXME: current v2 server implementation will ignore this message
                //        We might fix this in v3
                IMqttToken token = client.publish(iChans.getTopic("ctrl"), publishBody.toString().getBytes(), 2, true);
                token.waitForCompletion();
            }
        };
    
    /*
    Custom onDisconnect
    Can be overrided when init DAN
    */
    public void onDeregister(){
        return;
    }
    
    
    private void deregister()
        throws IOException, RegistrationError
    {
        if (client == null){
            return;
        }
        if (isRegisterFlag == false){
            return;
        }
        try{
            JSONObject deleteBody = new JSONObject();
            deleteBody.put("rev", rev);

            URL url = new URL(csmEndpoint+"/"+appID);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            OutputStream output = conn.getOutputStream();
            output.write(deleteBody.toString().getBytes());

            //get response
            int code = conn.getResponseCode();

            InputStream input;
            if(code>=400) {//error code
                input = new BufferedInputStream(conn.getErrorStream());
            }
            else {
                input = new BufferedInputStream(conn.getInputStream());
            }
            if(code!=200){
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                String texts = "";
                String readstring;
                while((readstring = reader.readLine())!=null){
                    texts = texts+readstring+"\n";
                }
                throw new RegistrationError(readstring);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String responseString;
            responseString = reader.readLine();
            reader.close();
            conn.disconnect();
            
            isRegisterFlag = false;
            onDeregister();  //call custom onDeregister
            
            //FIXME : return degister result
            JSONObject metadata = new JSONObject(responseString);
            
            logger.info("Successfully deregister.");
        } catch(JSONException e){
            throw new RegistrationError("Invalid response from server");
        } catch(MalformedURLException e){
            throw new RegistrationError("ConnectionError");
        }  
    }
    
    /*
    Custom onDisconnect
    Can be overrided when init DAN
    */
    public void onDisconnect(){
        return;
    }
    
    public void disconnect() 
        throws MqttException, RegistrationError, IOException, JSONException
    {
        //check if client is connected
        if (client == null){
            return;
        }
        if (client.isConnected() == false){
            return;
        }
        
        //if PersistentBinding flag is true, send offline.
        // else disconnect and deregister
        if (appID.isPersistentBinding()){
            JSONObject publishBody = new JSONObject();
            publishBody.put("state", "offline");
            publishBody.put("rev", rev);
            IMqttToken token = client.publish(iChans.getTopic("ctrl"), publishBody.toString().getBytes(), 2, true);
            token.waitForCompletion();
            IMqttToken t = client.disconnect();
            t.waitForCompletion(5000);
            onDisconnect();
            logger.info("Successfully disconnect.");
        }
        else{
            IMqttToken t = client.disconnect();
            t.waitForCompletion(5000);
            onDisconnect(); //call custom onDisconnect
            logger.info("Successfully disconnect.");
            logger.info("\"persistent_binding\" didn't set to True. Auto deregister after disconnent." );
            deregister();    //deregister this device
        }
        
    }
}
