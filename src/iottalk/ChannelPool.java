package iottalk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ChannelPool{
    private Map<String, DeviceFeature> dfName2DFMap;
    private Map<String, String> dfName2TopicMap;
    private Map<String, String> Topic2dfNameMap;
    public ChannelPool(){
        dfName2DFMap = new HashMap<>();
        dfName2TopicMap = new HashMap<>();
        Topic2dfNameMap = new HashMap<>();
    }
    
    public void addDF(DeviceFeature df){
        dfName2DFMap.put(df.getDFName(), df);
    }
    
    public void set(String dfName, String topic){
        dfName2TopicMap.put(dfName, topic);
        Topic2dfNameMap.put(topic, dfName);
    }
    
    public void remove(String name){
        String topic = dfName2TopicMap.get(name);
        dfName2TopicMap.remove(name);
        Topic2dfNameMap.remove(topic);
    }
    
    public String getDFName(String topic){
        return Topic2dfNameMap.get(topic);
    }
    
    public String getTopic(String dfn){
        return dfName2TopicMap.get(dfn);
    }
    
    public DeviceFeature getDFCbyName(String name){
        return dfName2DFMap.get(name);
    }
    
    public DeviceFeature getDFCbyTopic(String topic){
        return getDFCbyName(Topic2dfNameMap.get(topic));
    }
    
    //TODO implement items, return Array of all Map
}
