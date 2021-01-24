# IoTtalk v2 Java SDK

No main function in these code. Check [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java) for example.

## Dependent libraries
* [org.json](https://mvnrepository.com/artifact/org.json/json)
    * [Download jar](https://repo1.maven.org/maven2/org/json/json/20201115/json-20201115.jar)
* [org.eclipse.paho.client.mqttv3](https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3/1.2.5)
    * [Download jar](https://repo.eclipse.org/content/repositories/paho-releases/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar)

## How to use
* 若要用SA版本，請見 [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
* 若要自行撰寫DAI，下方有[範例程式](#自定義DAI範例)及class定義[說明](#DAN-Classes-說明)
* 自行編譯與打包成jar，可執行 `./create_jar.sh`


## 自定義DAI範例
```java=
package selfdefine;

import iottalk.DAN;
import iottalk.DeviceFeature;
import iottalk.AppID;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class DAI{
    private static boolean aliveFlag;
    
    public static void main(String[] args) throws Exception{
        Signal.handle(new Signal("INT"), new SignalHandler() {
            public void handle(Signal sig) {
                aliveFlag = false;
            }
        });
        aliveFlag = true;
        
        try{
            String csmEndpoint = "http://localhost:9992/csm";
            String[] acceptProtos = {"mqtt"};
            String deviceName = "Dummy_Test_java";
            String deviceModel = "Dummy_Device";
            AppID appId = new AppID();
            String userName = null;
            
            //DeviceFeature define
            DeviceFeature Dummy_Control = new DeviceFeature("Dummy_Control", "odf"){
                @Override
                public void onData(MqttMessage message, String df_name, String df_type){
                    try{
                        JSONArray odfValue = new JSONArray(new String(message.getPayload(), "UTF-8"));
                        System.out.println(odfValue);
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }
            };
            DeviceFeature Dummy_Sensor = new DeviceFeature("Dummy_Sensor", "idf");
            DeviceFeature[] dfList = new DeviceFeature[]{Dummy_Control, Dummy_Sensor};

            JSONObject putBodyPorfile = new JSONObject();
            putBodyPorfile.put("model", deviceModel);
            putBodyPorfile.put("u_name", userName);

            //new dan
            DAN dan = new DAN(csmEndpoint, acceptProtos, dfList, appId, deviceName, putBodyPorfile){
                @Override
                public boolean on_signal(String command, String df){
                    System.out.println(df+":"+command);
                    if (command.equals("CONNECT")){
                        System.out.println(df+":"+command);
                    }
                    else if (command.equals("DISCONNECT")){
                        System.out.println(df+":"+command);
                    }
                    return true;
                }
            };

            dan.register(); //register and connect

            //busy wait
            while(true){
                int randomNum = 1 + (int)(Math.random() * 100);
                int [] pushData = {randomNum};
                JSONArray r = new JSONArray(pushData);
                dan.push("Dummy_Sensor", r);  //push data

                if (aliveFlag == false){
                    break;
                }
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(500);
            }

            dan.disconnect();  //disconnect
        } catch(Exception e){
            throw e;
        }
    }
}
```

## DAN Classes 說明

### Class `DAN`

Constructor
---
```
public DAN(String _csmUrl, String[] _acceptProtos,  DeviceFeature[] _dfList, AppID _id, String _deviceName, JSONObject _profile)
throws JSONException, RegistrationError
```

* `_csmUrl` : csm endpoint的url。
* `_acceptProtos` : server運行的protocols。預設為`{"mqtt"}`。
* `_dfList` : 該Device所有df組成的Array。關於df的設定請見 [DeviceFeature](#Class-DeviceFeature)
* `_id` : 用來處理該Device於iottalk v2 server上的`UUID`。相關的設定請見 [AppID](#Class-AppID)
* `_deviceName` : 該Device的名子，可自訂。
* `_profile` : 其他的設定。預設需包含`model`；若為某user private使用，可以加上`u_name`。

Register
---
```
public void register()
throws IOException, ProtocolException, MqttException, RegistrationError
```

讓DAN向iottalk v2 csm註冊，並在註冊成功後自動連線。

Push
---
```
public boolean push(String idfName, JSONArray data)
throws MqttException, RegistrationError
```

向endpoint傳送資料。
* `push` : the idf name which data sent to.
* `data` : 為`JSONArray`。可以透過`JSONArray r = new JSONArray(pushData)`，將所要送出的值打包。

on_singal
---
`public boolean on_signal(String command, String df)`

當DAN收到server傳送的SIGNAL時，會呼叫此function
* `command` : server傳送的SIGNAL，可為`CONNECT`, `DISCONNECT`字串
    * use `command.equals("CONNECT")`, `command.equals("DISCONNECT")` to check which command it is.
* `df` : connect/disconnect 的df名稱，可以為idf或是odf。

Disconnect
---
```
public void disconnect()
throws MqttException, RegistrationError, IOException, JSONException
```

中止DAN與iottalk v2 csm的連線，若該device沒有固定的UUID，DAN會自動在中止連線後，向csm註銷(deregister)。
相關的設定請見 [AppID](#Class-AppID)

Other callback functions
---
以下4個callback會在對應的時機被DAN呼叫，若有需求，可以在宣告DAN時Override。
* `public void on_register()`
* `public void on_deregister()`
* `public void on_connect()`
* `public void on_disconnect()`
### Class `DeviceFeature`
Constructor : 
---
1. `public DeviceFeature(String df_name, String df_type)`
2. `public DeviceFeature(String df_name, String df_type, String[] paramtype)`

建立 Device Feature
* `df_name` : Device Feature的名稱。ex : `Dummy_Sensor`
* `df_type` : must be `idf` or `odf`
* `paramtype` : 此df的變數格式ex:`{"g", "g", "g"}`。若無此項，default = `{null}`

onData
---
`public void onData(MqttMessage message, String df_name, String df_type)`

若此df為ODF，當收到更新值時，會呼叫此function。在建立object時，必需Override此function。
* `message` : 從server收到的訊息。
* `df_name` : 該Device Feature的名稱
* `df_type` : `idf` or `odf`

publishData
---
```
public JSONArray publishData()
throws JSONException
```

若使用SA.java，需在IDF中設定此function，SA版本的DAI會在需要push該IDF時，呼叫此function，以取得所要push的資料。
若自行撰寫DAI，可忽略此function，並自行處理IDF push。

toString
---
```
@Override
public String toString()
```

return `DFType`:`DFName`

其他member function
---
與DAN互動會使用到，自行撰寫DAI，可參考使用。
* `public ArrayList<Object> getArrayList()`
* `public String getDFName()`
* `public void setDFName(String name)`
* `public String getDFType()`
* `public void setDFType(String type)`
* `public String[] getParamType()`
* `public void setParamType(String[] paramtype)`
* `public boolean isIDF()`
* `public boolean isODF()`
* `public IMqttMessageListener getCallBack()`

### Class `AppID`
此class用來處理該Device於iottalk v2 server上的`UUID`，在註冊時需傳給DAN。
`persistent_binding` : class中的一個flag。若為`true`，在斷線後DAN不會註銷該Device；若為`false`，在斷線後DAN會自動註銷該Device。

Constructor : 
---
1. `public AppID()`
2. `public AppID(String uuidHexDigitString)`
3. `public AppID(String uuidHexDigitString, boolean _persistent_binding)`
* `uuidHexDigitString` : 自訂的UUID，需為Hex String。ex : `aaaaa1234567890abcdef`

| `UUID`\\`persistent_binding` | `true` | `false` |
| -------- | -------- | -------- |
| 隨機生成   | \<Forbidden\>     |   Constructor `1`   |
| 自訂     | Constructor `2`  | Constructor `3` <br> `_persistent_binding` <br> must set to `false`      |

toString
---
```
@Override
public String toString()
```

return `UUID.toString()`

其他member function
---
與DAN互動會使用到，自行撰寫DAI，可參考使用。
* `public UUID getUUID()`
* `public void setUUID(UUID _uuid)`
* `public void setUUID(String uuidHexDigitString)`

### Class `ChannelPool`
管理`df name`, `topic name`, 與所對應的`Device Feature` object的對應關係，供DAN在執行時使用。

### Class `ColorBase`
定義log訊息的顏色。為`DAN.DANColor`的parent class。

### Class `DAI`
SA版本的DAI，為使用`SA`時所執行的DAI。
`SA`詳細說明與使用請見 : [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
