all: main

main: ./ChatServer.java ./ChatClient.java
	javac $^
