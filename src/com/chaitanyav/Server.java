package com.chaitanyav;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Chaitanya V
 */
public class Server {
    ServerSocket ssock;
    Vector<ClientHandler> clientHandlers = new Vector<>();
    
    //used to store session data. useful in case of reconnection after disconnect. must be cleaned after some interval.
    //the String here is the UUID that identifies a client uniquely
    //for future, server must allow overriding this id generation method so as to support custom ids such as phone numbers
    HashMap<String, Object> sessionData = new HashMap<>();
    volatile boolean running=false;
    
    //executes under context of clienthandler thread
    //declared here instead of static in clienthandler so that multiple servers can coexist in same application
    HashMap<String,Action> actions = new HashMap<>();
    
    //executor service. all ClientHandlers will share this
    //not static so as to support multiple servers in same application
    ExecutorService pool = Executors.newCachedThreadPool();
    
    //disconnect timeout and latency DEFAULT in MilliSeconds
    //for now latency is only checked for when the client doesn't sends any data for disconnectTimeout time
    long disconnectTimeout = 10000;   //default 10 seconds i.e. 10000ms
    long latency = 200;   //ping latency in ms
    final int port;
    
    
    
    Thread connectorThread = new Thread(){
        @Override
        public void run(){
            while(running){
                try {
                    Socket s = ssock.accept();
                    System.out.println("[SERVER] Socket connected - "+s);
                    int index=clientHandlers.size();    //max limit of clients is INT_MAX
                    clientHandlers.add(new ClientHandler(s,Server.this,index));                    
                } catch (IOException ex) {}
            }
        }
    };
    public Server(int port) {
        this(port,Constants.DISC_TIMEOUT,Constants.LATENCY);
    }
    public Server(int port,int disconnect_ms,int latency_ms) {
        System.out.println("[SERVER] Initializing....");
        this.port=port;
        disconnectTimeout=disconnect_ms;
        latency=latency_ms;
    }
    
    public void start() throws IOException{
        running=true;
        ssock = new ServerSocket(port);
        connectorThread.start();
        System.out.println("[SERVER] Done. Started!");
    }
    public void stop(){
        System.out.println("[SERVER] Stopping server...");
        running=false;
        try {
            ssock.close();
            for(ClientHandler hnd:clientHandlers){
                hnd.stop();
            }
        } catch (IOException ex) {}
        System.out.println("[SERVER] Server stopped!");
    }
    
    
    public void setAction(String tag,Action action) throws Exception{
        if(tag.equals(Constants.PONG) || actions.containsKey(tag)){
            throw new Exception("Action already exists!");
        }
        else actions.put(tag, action);
    }
    
}
