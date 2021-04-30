package iottalk;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class DeviceFeature{
    private String DFName;
    private String DFType;
    private String[] ParamType;
    
    final private IMqttMessageListener MessageListener = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message){
            try{
                pullDataCB(message, DFName);
            }
            catch(Exception e){
                e.printStackTrace();
                System.out.println("Exception happened in onData : "+e);
            }
            
        }
    };
    
    public DeviceFeature(String df_name, String df_type, String[] paramtype){
        DFName = df_name;
        DFType = df_type;
        ParamType = paramtype;
    }
    
    public DeviceFeature(String df_name, String df_type){
        DFName = df_name;
        DFType = df_type;
        ParamType = new String[] {null};
    }
    
    public ArrayList<Object> getArrayList(){
        ArrayList<Object> r = new ArrayList<Object>();
        List<String> ParamTypeList = Arrays.asList(ParamType);
        r.add(DFName);
        r.add(ParamTypeList);
        return r;
    }
    
    public String getDFName(){
        return DFName;
    }
    public void setDFName(String name){
        DFName = name;
    }
    public String getDFType(){
        return DFType;
    }
    public void setDFType(String type){
        if (type.equals("idf") || type.equals("odf")){
            DFType = type;
            return;
        }
        // TODO : Raise Error;
        return ;
    }
    public String[] getParamType(){
        return ParamType;
    }
    public void setParamType(String[] paramtype){
        ParamType = paramtype;
    }
    public boolean isIDF(){
        return DFType.equals("idf");
    }
    public boolean isODF(){
        return DFType.equals("odf");
    }
    public IMqttMessageListener getCallBack(){
        return MessageListener;
    }
    
    public void pullDataCB(MqttMessage message, String df_name){
        return;
    }
    public JSONArray getPushData() throws JSONException{
        String [] pushData = {null};
        JSONArray r = new JSONArray(pushData);
        return r;
    }
    
    @Override
    public String toString(){
        return DFType+":"+DFName;
    }
}
