JC = javac

BIN_DIR = bin
BINS_DIR = bin/iottalk
SRC_DIR = src/iottalk

SRCS = $(wildcard $(SRC_DIR)/*.java)
BINS = $(SRCS:$(SRC_DIR)/%.java=$(BINS_DIR)/%.class)
CPFLAG = -cp "bin:libs/*"
DFLAG = -d $(BIN_DIR)

iottalk.jar: $(BINS)
	cd bin && \
	jar cfe iottalk.jar iottalk.DAI iottalk/* && \
	mv iottalk.jar .. 

compile: $(BINS)

$(BINS_DIR)/%.class: $(SRC_DIR)/%.java
	  mkdir -p bin
	  $(JC) $(CPFLAG) $(DFLAG) -Xlint:none $(SRCS)

clean:
	@if [ -f iottalk.jar ] ; then \
	rm iottalk.jar; \
	fi
