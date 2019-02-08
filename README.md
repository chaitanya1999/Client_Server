# Client_Server
A simple multithreaded client server implementation with existing support for KeepAlive(aka Heartbeat or pingpong) mechanism.


How to use this?
To use this, you only need an object of com.chaitanyav.Server class.
The two constructors are - 
public Server(int port)
public Server(int port,int disconnect_ms,int latency_ms)

Server svr = new Server(25565,5000,250);

The first one uses default disconnect timeout and latency which are by default 10000ms and 500ms. When the client doesn't responds i.e. no 
data transfer actually took place between the last disconnect timeout time (which is by default 10 seconds i.e. 10000ms), then the Server
sends a ping msg to check whether the client is still connected or has disconnected, and then waits for time equal to the latency parameter
passed in the constructor. If within this time, the client responds with a pong, then the connection is considered to be still alive.
Otherwise, the client is interpreted as disconnected and the connection is broken.

After creating object of the Server, you must add action. Action specifies what code must be executed when a particular message has been 
received from the client.

svr.setAction("My_Message",new Action(){
  @Override
  public void execute(ClientHandler cw, Message msg){
    //...code
    //...code
    //...code
  }
});

The message class encapsulates the message received from the client. It has two fields. Tag and Data. The string "My_Message" is known as
tag i.e. which specifies the type of action or data. Second one is Data which contains the object you passed from the client with the 
message i.e. the details of the message.

To send message back to client use cw.sendData(Message msg) and cw.sendDataHandleDisconnect(Message msg)

E.g.- 
svr.setAction("ArraySum",new Action()){
  @Override
  public void execute(ClientHandler cw, Message msg){
    Integer arr[] = (Integer[]) msg.getData();
    Integer sum=0;
    for(Integer x:arr)sum+=x;
    cw.sendDataHandleDisconnect(new Message("ArraySum",sum));
  }
}

sendData method simply sends data to the client and throws SocketException if any failure occurs. sendDataHandleDisconnect on the other 
hand treats the exception as if the client is disconnected doesn't throws any exception.

The Client class is currently under development. For now you can connect to server using simple Socket and then you must use Object streams
to communicate with the server using the class Message to encapsulate data.

