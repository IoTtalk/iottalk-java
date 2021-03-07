# IoTtalk v2 Java SDK

這是 IoTtalk v2 Java 版的函式庫。
實際的main function範例程式請參考 [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java) 。

## 需要的函式庫
已經有放在資料夾`libs`裡面了，不用另外下載。
* [org.json](https://mvnrepository.com/artifact/org.json/json) : 版本需求 >= 20131018
* [org.eclipse.paho.client.mqttv3](https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3/1.2.5) : 版本需求 == 1.2.5

## 如何使用
有兩種方法。可以`使用SA版本`，或是`自行撰寫DAI`
### 使用SA版本
SA版本提供main function，使用者只需要在`SA.java`中改變參數即可。
連結 : [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
### 自行撰寫DAI
若對於SA版本的main行為不滿足，可自行撰寫main，下方有[範例程式](#自定義DAI範例)及class定義[說明](#DAN-Classes-說明)

### 將此函式庫打包成jar檔
執行 `./create_jar.sh`，將會自動產生`iottalk.jar`，供需要自行撰寫dai者使用。

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
* `_id` : 用來處理該Device於iottalk v2 server上的`mac_addr`。相關的設定請見 [AppID](#Class-AppID)
* `_deviceName` : 該Device的名字，可自訂。
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

向 iottalk server 傳送指定 df 的資料。
* `idfName` : idf的名字.
* `data` : 為`JSONArray`。可以透過`JSONArray r = new JSONArray(pushData)`，將所要送出的值打包。

On Singal
---
`public boolean on_signal(String command, String df)`

當DAN收到server傳送的SIGNAL時，會呼叫此function
* `command` : server傳送的SIGNAL，可為`CONNECT`, `DISCONNECT`字串。
    * 可用 `command.equals("CONNECT")`, `command.equals("DISCONNECT")` 來檢查是哪種signal.
* `df` : connect/disconnect 對應的df名字。

Disconnect
---
```
public void disconnect()
throws MqttException, RegistrationError, IOException, JSONException
```

中止DAN與iottalk v2 csm的連線，若該device沒有固定的mac_addr，DAN會自動在中止連線後，向csm註銷(deregister)。
相關的設定請見 [AppID](#Class-AppID)

其他的 callback functions
---
以下4個callback會在對應的時機被DAN呼叫，若有需求，可以在宣告DAN時，使用Override來改變其功能。
* `public void on_register()` : 在DAN向csm註冊成功後被呼叫。
* `public void on_deregister()` : 在DAN向csm註銷成功後被呼叫。
* `public void on_connect()` : 在DAN向csm成功建立mqtt連線後被呼叫。
* `public void on_disconnect()` : 在DAN向csm正常中斷mqtt連線後被呼叫。
### Class `DeviceFeature`
Constructor : 
---
1. `public DeviceFeature(String df_name, String df_type)`
2. `public DeviceFeature(String df_name, String df_type, String[] paramtype)`

建立 Device Feature
* `df_name` : Device Feature的名稱。ex : `Dummy_Sensor`
* `df_type` : 必需是 `idf` 或是 `odf`
* `paramtype` : 此df的變數格式ex:`{"g", "g", "g"}`。若無此項，預設值為 `{null}`

On Data
---
`public void onData(MqttMessage message, String df_name, String df_type)`

若此df為ODF，當收到更新值時，會呼叫此function。在建立ODF object時，必需Override此function。 此函式是 DAN 中 ODF callback function 的目標。
* `message` : 從server收到的訊息。
* `df_name` : 該Device Feature的名稱。
* `df_type` : `idf` or `odf`。

Publish Data
---
```
public JSONArray publishData()
throws JSONException
```

若使用SA.java，需在建立IDF object時，Override此function。 SA版本的DAI會在需要push該IDF時，呼叫此function，以取得所要push的資料。 <br>
若自行撰寫DAI，可忽略此function，並自行處理IDF push。

toString
---
```
@Override
public String toString()
```

回傳 字串`DFType`:`DFName`

其他member function
---
與DAN互動會使用到，自行撰寫DAI，可參考使用。
* `public ArrayList<Object> getArrayList()` : 以List的格是回傳該df的資訊。(註冊device時會用到)
* `public String getDFName()` : 回傳該df的名稱。
* `public void setDFName(String name)` : 設定該df的名稱。
* `public String getDFType()` : 回傳`idf`或是`odf`。
* `public void setDFType(String type)` : 設定該df為`idf`或是`odf`。
* `public String[] getParamType()` : 回傳該df的變數格式。
* `public void setParamType(String[] paramtype)` : 變更該df的變數格式。
* `public boolean isIDF()` : 回傳該df是否為idf。
* `public boolean isODF()` : 回傳該df是否為odf。
* `public IMqttMessageListener getCallBack()` : 回傳該df中所定義的 mqtt callback function。(訂閱mqtt subscriber時會用到)

### Class `AppID`
此class用來處理該Device於iottalk v2 server上的`mac_addr`，在註冊時需傳給DAN。
`persistent_binding` : class中的一個flag。若為`true`，在斷線後DAN不會註銷該Device；若為`false`，在斷線後DAN會自動註銷該Device。

Constructor : 
---
1. `public AppID()`
2. `public AppID(String uuidHexDigitString)`
3. `public AppID(String uuidHexDigitString, boolean _persistent_binding)`
* `uuidHexDigitString` : 自訂的mac_addr，需為Hex String。ex : `aaaaa1234567890abcdef`

| `mac_addr`\\`persistent_binding` | `true` | `false` |
| -------- | -------- | -------- |
| 隨機生成   | \<Forbidden\>     |   Constructor `1`   |
| 自訂     | Constructor `2`  | Constructor `3`      |

toString
---
```
@Override
public String toString()
```

回傳`mac_addr`的值。

其他member function
---
與DAN互動會使用到，自行撰寫DAI，可參考使用。
* `public UUID getUUID()` : 取得`mac_addr`。回傳值的class為`UUID`，若需要取得字串，請用`toString`。
* `public void setUUID(UUID _uuid)` : class `UUID` 設定 `mac_addr`。
* `public void setUUID(String uuidHexDigitString)` : 使用字串設定 `mac_addr`。

### Class `ChannelPool`
管理`df name`, `topic name`, 與所對應的`Device Feature` object的對應關係，供DAN在執行時使用。

### Class `ColorBase`
定義log訊息的顏色。為`DAN.DANColor`的parent class。

### Class `DAI`
SA版本的DAI，使用SA版本時，即是執行此DAI。
SA版本詳細說明與使用請見 : [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
