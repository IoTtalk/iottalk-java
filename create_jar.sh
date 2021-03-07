if [ ! -f libs/json-*.jar ]; then
  wget https://repo1.maven.org/maven2/org/json/json/20201115/json-20201115.jar -P libs
fi
if [ ! -f "libs/org.eclipse.paho.client.mqttv3-1.2.5.jar" ]; then
  wget https://repo.eclipse.org/content/repositories/paho-releases/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar -P libs
fi
mkdir -p bin
javac -cp "libs/*" -d bin src/iottalk/*
cd bin
jar cvf iottalk.jar iottalk/*
mv iottalk.jar ..
cd ..
