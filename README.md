# Client_Server
A simple multithreaded client server implementation with existing support for KeepAlive(aka Heartbeat or pingpong) mechanism.



# How to use this?
There are two classes Server and Client. You can use them either by extending or by directly using them as it is. Some special methods to be overridden after extending will be discussed later.




# Setting up Server



## **Step 1: Creating server object**


```
public Server(int port)
```
It creates a server with specified port, default pingInterval (10000 ms i.e. 10 seconds) for heartbeat messages and default and default pongTimeout(500 ms), the amount of time to wait for a pong in response to a ping from the client. Default constant values are available in **Constants** interface.


```
public Server(int port, int pingInterval_ms, int pongTimeout_ms)
```
It creates a server with specified port, pingInterval_ms as ping interval time and pongTimeout_ms as pong timeout.

Example-
```
Server svr = new Server(25565,30000,250);
```
This creates a server that runs on port 25565, sends heartbeat messages every 30 seconds and waits for atmost 250 milliseconds for a pong message after sending a ping to the client. If pong doesn't arrives in time, the client is assumed to be disconnected.




## **Step 2: Configuring server**


### Setting Action


**Action** is an abstract class that must be extended and its **execute** method be overridden so as to specify what code is to be executed for what message from the client. Each and every message is encapsulated as an object of **Message** class, which contains two fields, the TAG and the DATA. TAG is a string and is used to identify the purpose of data. DATA field is a reference to an Object, which must be type casted to its appropriate type.

Use the method serverObj.setAction()
```
public void setAction(String tag,Action action) throws Exception
```

Example-The client has sent a message with an Integer in its data field which must be incremented by the server and resent.
```
try{
  svr.setAction("increment_the_number", new Action(){
    @Override
    public void execute(ClientHandler hnd, Message msg){
      Integer x = (Integer)msg.getData();
      x=x+1;
      hnd.sendData("increment_the_number-response",x);
    }
  });
} catch(Exception e){
  //will throw exception if multiple actions are assigned to the same TAG, or if some reserved TAG is used
}
```


### Sending data to client

For every client, the server creates an object of class ClientHandler implicitly which contains the methods and threads required for each client-server connection and communication.

For sending data, use the method **clientHandler.sendData(tag,data)**. If this method fails to send data due to some error or due to connection being broken, it makes a call to **onMsgSendingFailed** of the **Server** class to take some appropriate action. This method can be overridden while extending the Server class. It is optional. Reference to clientHandler can be obtained from action's execute method.


### Disabling Heartbeat mechanism

By default heartbeat machanism is enabled. To disable it use the method **svr.disableHeartbeat()**, where svr is the server object.

```
public void disableHeartbeat()
```

### Stopping ClientHandler


```
public void stop()
```
This method stops the client handler for the current client, and blocks the thread, till the client handler performs some cleanup process.


```
public void stopNonBlocking()
```
This method stops the client handler, but doesn't blocks the thread. The cleanup process of the client handler is performed in background.




## **Step 3: Starting the server**


```
svr.start()
```
This starts the server. The server now is ready to accept connections.

Similarlt **svr.stop()** can be used to stop the server.




# Setting up the client



## **Step 1: Creating Client object**


```
public Client(String host, int port);
```
Creates a Client bound to a server identified by host and port. The port is port number of the server.


```
public Client(String host,int port,int pingInterval,int pongTimeout)
```

Creates a client bound to server identified by host and port and with the specified pingInterval and pongTimeout.

The heartbeat mechanism (used to detect a broken connection) is provided on both sides, i.e. client and server both keep pinging each other to detect if the connection is alive or broken.

Example-
```
Client clt = new Client("192.168.1.5",25565,10000,500);
```



## **Step 2: Configuring Client**


The client is provided with the autoconnect functionality, i.e. if the client detects a broken connection, if will try to reconnect. By default, this feature is disabled. To enable it, use the method **setToAutoConnect**.

```
public void setToAutoConnect(int maxTries);
```
This method turns on the autoconnect feature. **maxTries** specifies the maximum number of tries to reconnect to the server. If maxTries is 0, the client will try to reconnect infinitely.



### Communication - Receiving Data

To respond to messages of server, actions must be registered on the client side.

```
public void setAction(String tag, Action action) throws Exception
```

Registers the given action for messages identified by the string **tag**. Throws exception if multiple actions are registered for the same **tag** or if a reserved tag is used.

Example-
```
try{
  clt.setAction("increment_the_number-response",new Action(){
    @Override
    public void execute(Client clt,Message msg){
      System.out.println("The incremented number is " + ((Integer)msg.getData()));
    }
  });
}catch(Exception e){
  e.printStackTrace();
}
```

NOTE- There are two seperate Action classes for server and client, belonging to package **com.chaitanyav.server.Action** and **com.chaitanyav.client.Action**.




### Communication - Sending Data

Use **client.sendData(tag,msg)** method to send data.
This method can be used from anywhere, where there is a reference to Client object available.

If some error occurs due to the connection being broken during the sending of data, the client is assumed to be disconnected and the method **onMsgSendingFailed** is called, and if autoconnect is enabled, reconnection occurs. To use this method, it must be overridden after extending the Client class.



### Disabling Heartbeat mechanism

By default heartbeat machanism is enabled. To disable it use the method **clt.disableHeartbeat()**, where clt is the client object.

```
public void disableHeartbeat()
```



## **Step 3: Starting the client**


```
public void connect()
```
This method is used to start the client.
Ex - **clt.connect()**

To stop a client, the method clt.stop() can be called.






# Extending classes


The server and client classes are provided with methods that must be overridden in its child class to extend its functionality.


## Server Methods


### onMsgSendingFailed(ClientHandler hnd,Message msg)

This method is called when an IOException occurs while writing data to socket stream i.e. in the sendData method. After this method is called, the client is considered to be disconnected and the ClientHandler is stopped.




### onClientConnected(ClientHandler hnd)

Called when a new client connects to the server and the ClientHandler fully starts functioning. Called at the end of ClientHandler constructor. **Long running operations must be executed in a different thread as it can block the server's new connection accepting thread.**




### onClientDisconnected(ClientHandler hnd)

Called when ClientHandler stops while the server is still running. **Long running operationg must be performed in different thread**.




### onServerStopping()

Called first, when the server.stop() method is called.




### newClientHandler(Socket socket,Server server)

The server calls this method to get an instance of the ClientHandler for the new connection.
By default its body is-

```
protected ClientHandler newClientHandler(Socket socket,Server server){
    return new ClientHandler(socket, server);
}
```

This method must be necessarily overridden when the ClientHandler class is extended, so as to provide the server with instance of ClientHandler's child class.

The ClientHandler doesn't provides any overridable methods by default and relies on the Server's methods.



## Client methods



### onMsgSendingFailed(Message msg)

Called when an IOException occurs while writing data to socket stream. The client is then assumed to be disconnected and other methods are called accordingly.



### onDisconnect()

Called when the client disconnects from the server



### onConnect() 

Called when the client succesfully connects to the server










