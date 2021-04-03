JC = javac

BIN_DIR = bin
BINS_DIR = bin/iottalk
SRC_DIR = src/iottalk
LIB_DIR = libs

SRCS = $(wildcard $(SRC_DIR)/*.java)
BINS = $(SRCS:$(SRC_DIR)/%.java=$(BINS_DIR)/%.class)
CPFLAG = -cp "bin:libs/*"
DFLAG = -d $(BIN_DIR)

JSON_JAR_URL = https://repo1.maven.org/maven2/org/json/json/20201115/json-20201115.jar
PAHO_JAR_URL = https://repo.eclipse.org/content/repositories/paho-releases/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar

JSON_JAR_NAME = $(LIB_DIR)/json-20201115.jar
PAHO_JAR_NAME = $(LIB_DIR)/org.eclipse.paho.client.mqttv3-1.2.5.jar

iottalk.jar: $(BINS)
	cd bin && \
	jar cfe iottalk.jar iottalk.DAI iottalk/* && \
	mv iottalk.jar .. 

compile: $(BINS) 

$(BINS_DIR)/%.class: $(SRC_DIR)/%.java $(BIN_DIR)
	  make check_jar
	  $(JC) $(CPFLAG) $(DFLAG) -Xlint:none $(SRCS)

clean:
	@if [ -f iottalk.jar ] ; then \
	rm iottalk.jar; \
	fi

check_jar: $(JSON_JAR_NAME) $(PAHO_JAR_NAME)

$(JSON_JAR_NAME):
	@if [ ! -f $(JSON_JAR_NAME) ]; then \
	  wget $(JSON_JAR_URL) -P $(LIB_DIR);\
	fi

$(PAHO_JAR_NAME):
	@if [ ! -f $(PAHO_JAR_NAME) ]; then\
	  wget $(PAHO_JAR_URL) -P $(LIB_DIR); \
	fi

$(BIN_DIR):
	mkdir -p $(BIN_DIR)
