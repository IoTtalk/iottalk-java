# IoTtalk v2 Java SDK

這是 IoTtalk v2 Java 版的函式庫。
實際的範例程式請參考 [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java) 。

## 環境需求
* git
* make
* [OpenJDK](https://openjdk.java.net/install/) : JDK 版本需求 >= 8
* 需要的 jar 函式庫 <br>
**使用指令 `make check_jar` 會自動下載所需 jar 的預設版本。**
   * [org.json](https://mvnrepository.com/artifact/org.json/json) : 版本需求 >= 20131018 , 預設版本 : 20210307
   * [org.eclipse.paho.client.mqttv3](https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3/1.2.5) : 版本需求 >= 1.2.5 , 預設版本 : 1.2.5

## 如何使用
有兩種版本。可以`使用 SA 版本`，或是`自行撰寫 DAI`
### 使用 SA 版本
SA 版本 : 使用者只需要在 `SA.java` 中改變參數即可，無需理會下方 DAI 的說明。
連結 : [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
### 自行撰寫 DAI
若對於 SA 版本的行為不滿足，可自行撰寫 DAI (即程式的 main function)，下方有[範例程式](#自定義DAI範例)及 class 定義[說明](#DAN-Classes-說明)

### 將此函式庫打包成 jar 檔
執行 `make iottalk.jar` ，將會自動產生 `iottalk.jar` ，供需要自行撰寫 DAI 者使用。

## 自定義DAI範例
```java
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
            AppID deviceAddr = new AppID();
            String userName = null;
            
            //DeviceFeature define
            DeviceFeature Dummy_Control = new DeviceFeature("Dummy_Control", "odf"){
                @Override
                public void pullDataCB(MqttMessage message, String df_name, String df_type){
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

            JSONObject registerPorfile = new JSONObject();
            registerPorfile.put("model", deviceModel);
            registerPorfile.put("u_name", userName);

            //new dan
            DAN dan = new DAN(csmEndpoint, acceptProtos, dfList, deviceAddr, deviceName, registerPorfile){
                @Override
                public boolean onSignal(String command, String df){
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

**Constructor**

```java
public DAN(String _csmUrl, String[] _acceptProtos,  DeviceFeature[] _dfList, AppID _deviceAddr, String _deviceName, JSONObject _profile)
throws JSONException, RegistrationError
```

* `_csmUrl` : csm endpoint 的 url。
* `_acceptProtos` : server 運行的 protocols。預設為`{"mqtt"}`。
* `_dfList` : 該 Device 所有 df (device feature) 組成的 Array。關於 df 的設定請見 [DeviceFeature](#Class-DeviceFeature)
* `_deviceAddr` : 用來處理該 Device 於 iottalk v2 server上的 `device_addr` 。相關的設定請見 [AppID](#Class-AppID)
* `_deviceName` : 該 Device 的名字，可自訂。
* `_profile` : 其他的設定。預設需包含 `model` ；若為某 user private 使用，可以加上 `u_name` 。

**Register**

```java
public void register()
throws IOException, ProtocolException, MqttException, RegistrationError
```

DAN 向 iottalk v2 csm 註冊，並在註冊成功後自動連線。

**Push**

```java
public boolean push(String idfName, JSONArray data)
throws MqttException, RegistrationError
```

向 iottalk server 傳送指定 df 的資料。
* `idfName` : idf 的名字.
* `data` : 為`JSONArray`。可以透過 `JSONArray r = new JSONArray(pushData)` ，將所要送出的值打包。
* 回傳值 : push 成功與否。

**On Singal**

`public boolean onSignal(String command, String df)`

當 DAN 收到 server 的 SIGNAL 時，會呼叫此 function
* `command` : server 的 SIGNAL，可為 `CONNECT` , `DISCONNECT` 字串。
    * 可用 `command.equals("CONNECT")`, `command.equals("DISCONNECT")` 來檢查是哪種 signal.
* `df` : connect/disconnect 對應的 device feature 。

**Disconnect**

```java
public void disconnect()
throws MqttException, RegistrationError, IOException, JSONException
```

中止 DAN 與 iottalk v2 csm 的連線，若該 device 沒有固定的 device_addr，DAN 會自動在中止連線後，向 csm 註銷(deregister)。
相關的設定請見 [AppID](#Class-AppID)

**其他的 callback functions**

以下 4 個 callback 會在對應的時機被 DAN 呼叫，若有需求，可以在宣告 DAN 時，使用 Override 來改變其功能。
* `public void onRegister()` : 在 DAN 向 csm 註冊成功後被呼叫。
* `public void onDeregister()` : 在 DAN 向 csm 註銷成功後被呼叫。
* `public void onConnect()` : 在 DAN 向 csm 成功建立 mqtt 連線後被呼叫。
* `public void onDisconnect()` : 在 DAN 向 csm 正常中斷 mqtt 連線後被呼叫。
### Class `DeviceFeature`
**Constructor**

```java
public DeviceFeature(String df_name, String df_type)
public DeviceFeature(String df_name, String df_type, String[] paramtype)
```

建立 Device Feature
* `df_name` : Device Feature 的名稱。ex : `Dummy_Sensor`
* `df_type` : 必需是 `idf` 或是 `odf`
* `paramtype` : 此 df 的變數格式 ex:`{"g", "g", "g"}`。若無此項，預設值為 `{null}`

**Pull Data Callback**

`public void pullDataCB(MqttMessage message, String df_name, String df_type)`

若此 df 為 ODF，當收到更新值時，會呼叫此 function。在建立 ODF object 時，必需 Override 此 function。 此函式是 DAN 中 ODF callback function 的目標。
* `message` : 從 server 收到的訊息。
* `df_name` : 該 Device Feature 的名稱。
* `df_type` : `idf` or `odf`。

**Push Data**

```java
public JSONArray getPushData()
throws JSONException
```

若使用 SA 版本，需在建立 IDF object 時，需要 Override 此 function。 SA 版本的 DAI 會在該 IDF 需要 push 時，呼叫此 function ，以取得要 push 的資料。 <br>
若自行撰寫 DAI，可忽略此 function，並自行處理 IDF push。

**toString**

```java
@Override
public String toString()
```

回傳 字串 `DFType`:`DFName`

**其他的 member functions**

與 DAN 互動會使用到，自行撰寫 DAI，可參考使用。
* `public ArrayList<Object> getArrayList()` : 以 List 的格是回傳該 df 的資訊。(註冊 device 時會用到)
* `public String getDFName()` : 回傳該 df 的名稱。
* `public void setDFName(String name)` : 設定該 df 的名稱。
* `public String getDFType()` : 回傳 `idf` 或是 `odf`。
* `public void setDFType(String type)` : 設定該 df 為 `idf` 或是 `odf`。
* `public String[] getParamType()` : 回傳該 df 的變數格式。
* `public void setParamType(String[] paramtype)` : 變更該 df 的變數格式。
* `public boolean isIDF()` : 回傳該 df 是否為 idf。
* `public boolean isODF()` : 回傳該 df 是否為 odf。
* `public IMqttMessageListener getCallBack()` : 回傳該 df 中所定義的 mqtt callback function。(訂閱 mqtt subscriber 時會用到)

### Class `AppID`
此 class 用來處理該 Device 於 iottalk v2 server上的 `device_addr`，在註冊時需傳給 DAN。
`persistent_binding` : class 中的一個 flag。若為 `true`，在斷線後 DAN 不會註銷該 Device；若為 `false`，在斷線後 DAN 會自動註銷該 Device。

**Constructor**

```java
public AppID()
public AppID(String uuidHexDigitString)
public AppID(String uuidHexDigitString, boolean _persistent_binding)
```
* `uuidHexDigitString` : 自訂的 device_addr，需為 Hex String。 ex : `aaaaa1234567890abcdef`

| `mac_addr`\\`persistent_binding` | `true` | `false` |
| -------- | -------- | -------- |
| 隨機生成   | \<Forbidden\>     |   Constructor `1`   |
| 自訂     | Constructor `2`  | Constructor `3`      |

**toString**

```
@Override
public String toString()
```

回傳 `device_addr` 的值。

**其他的 member functions**

與 DAN 互動會使用到，自行撰寫 DAI，可參考使用。
* `public UUID getUUID()` : 取得 `device_addr`。回傳值的 class 為 `UUID` ，若需要取得字串，請用 `toString`。
* `public void setUUID(UUID _uuid)` : class `UUID` 設定 `device_addr`。
* `public void setUUID(String uuidHexDigitString)` : 使用字串設定 `device_addr`。

### Class `ChannelPool`
管理 `df name`, `topic name`, 與所對應的 `Device Feature` object 的對應關係，供 DAN 在執行時使用。

### Class `ColorBase`
定義 log 訊息的顏色。為 `DAN.DANColor` 的 parent class。

### Class `DAI`
SA 版本的 DAI，使用 SA 版本時，即是執行此 DAI。
SA 版本詳細說明與使用請見 : [Dummy_Device_IoTtalk_v2_java](https://github.com/IoTtalk/Dummy_Device_IoTtalk_v2_java)
