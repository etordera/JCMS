TARGET=lcmstojava
B=bin
S=src
O=obj
LIBS=-llcms2

all: $(B)/$(TARGET)

$(B)/$(TARGET): $(O)/lcmstojava.o
	test -d $(B) || mkdir $(B)
	g++ -o $(B)/$(TARGET) $^ $(LIBS) 

$(O)/lcmstojava.o: $(S)/lcmstojava.cpp
	test -d $(O) || mkdir $(O)
	g++ -c -o $(O)/lcmstojava.o $(S)/lcmstojava.cpp
		
clean:
	rm $(O)/*.o
	rm $(B)/*
	
run: all
	$(B)/lcmstojava

