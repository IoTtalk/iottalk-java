mkdir bin
javac -cp "libs/*" -d bin src/iottalk/*
cd bin
jar cvf iottalk.jar iottalk/*
mv iottalk.jar ..
cd ..
