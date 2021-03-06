package com.chaitanyav.server;

import com.chaitanyav.Constants;
import static com.chaitanyav.Utils.*;
import com.chaitanyav.Message;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Chaitanya V
 */
public class Server {
    
    private ServerSocket ssock;
    private Vector<ClientHandler> clientHandlers = new Vector<>();
    
    //used to store session data. useful in case of reconnection after disconnect. must be cleaned after some interval.
    //the String here is the UUID that identifies a client uniquely
    //for future, server must allow overriding this id generation method so as to support custom ids such as phone numbers
    private HashMap<String, Object> sessionData = new HashMap<>();
    
    private volatile boolean running=false;
    
    //executes under context of clienthandler thread
    //declared here instead of static in clienthandler so that multiple servers can coexist in same application
    final private HashMap<String,Action> actions = new HashMap<>();
    
    //executor service. all ClientHandlers will share this
    //not static so as to support multiple servers in same application
    private ExecutorService cachedPool = Executors.newCachedThreadPool();
    
    
    //disconnect timeout and pongTimeout DEFAULT in MilliSeconds
    //for now pongTimeout is only checked for when the client doesn't sends any data for pingInterval time
    private long pingInterval = 10000;   //default 10 seconds i.e. 10000ms
    private long pongTimeout = 200;   //ping pongTimeout in ms
    private final int port;
    
    private boolean heartbeat = true;
    
    
    
    Thread connectorThread = new Thread(){
        @Override
        public void run(){
            while(running){
                try {
                    Socket s = ssock.accept();
                    log("[SERVER] New socket connected - "+s);
                    clientHandlers.add(newClientHandler(s,Server.this));        
                } catch (IOException ex) {}
            }
        }
    };
    
    /**
     * Creates a Server object with the given port and default pingInterval time and pongTimeout
     * @param port 
     */
    public Server(int port) {
        this(port,Constants.PING_INTERVAL,Constants.PONG_TIMEOUT);
    }
    
    /**
     * @param port The port on which the server must run
     * @param pingInterval_ms The disconnect timeout time to check if connection is alive or not
     * @param pongTimeout_ms The amount of time to wait for 'pong' after 'ping' has been sent to the client after inactivity for disconnect_ms time.
     */
    public Server(int port,int pingInterval_ms,int pongTimeout_ms) {
        log("[SERVER] Initializing....");
        this.port=port;
        pingInterval=pingInterval_ms;
        pongTimeout=pongTimeout_ms;
    }
    
    
    /**
     * Starts the server
     * @throws IOException 
     */
    public void start() throws IOException{
        running=true;
        ssock = new ServerSocket(port);
        connectorThread.start();
        log("[SERVER] Done. Started!");
    }
    
    /**
     * Stops the server
     */
    final public void stop(){
        if(running==false)return;
        onServerStopping();
        log("[SERVER] Stopping server...");
        running=false;
        try {
            ssock.close();
            for(ClientHandler hnd:clientHandlers){
                hnd.stopNonBlocking();
            }
        } catch (IOException ex) {}
        cachedPool.shutdown();
        log("[SERVER] Server stopped!");
    }
    
    //setters and getters
    
    /**
     * Registers an Action object representing the action to be taken when a
     * message with the particular tag is received
     * @param tag The tag to invoke the action.
     * @param action The action to be performed for the corresponding tag.
     * @throws Exception 
     */
    final public void setAction(String tag,Action action) throws Exception{
        if(tag.equals(Constants.PING) || tag.equals(Constants.PONG) ||actions.containsKey(tag)){
            throw new Exception("Action already exists!");
        }
        else actions.put(tag, action);
    }
    
    final Vector<ClientHandler> getClientList(){
        return clientHandlers;
    }
    
    /**
     * Returns a boolean representing the running status of the Server
     * @return TRUE if server is running, else FALSE
     */
    final public boolean isRunning(){
        return running;
    }
    
    /**
     * Returns the corresponding action object for a particular tag, if exists
     * otherwise null
     * @param tag
     * @return Action object or null
     */
    final public Action getAction(String tag){
        return actions.get(tag);
    }
    
    /**
     * Executes a Runnable on the thread pool
     * @param privatePool
     * @param runnable The code to be executed.
     * @param seperateThread
     */
    final void executeAction(ExecutorService privatePool,Runnable runnable,boolean seperateThread){
        if(seperateThread)cachedPool.submit(runnable);
        else privatePool.submit(runnable);
    }
    
    /**
     * Returns the disconnection timeout
     * @return pingInterval
     */
    final public long getPingInterval(){
        return pingInterval;
    }
    
    /**
     * Returns the pongTimeout in milliseconds
     * @return pongTimeout
     */
    final public long getPongTimeout(){
        return pongTimeout;
    }
    
    /**
     * Returns the port on which the Server is running.
     * @return The port number.
     */
    final public int getPort(){
        return port;
    }
    //
    
    /**
     * This method is called when sendData method fails to send data to the client.
     * It is called before disconnection process starts for that client.
     * It can be used for e.g. to store the message in a database to be delivered
     * later to the same client.
     * It must be overridden in the child class. 
     * @param hnd The reference to the ClientHandler of the disconnecting client.
     * @param msg The message which wasn't sent
     */
    protected void onMsgSendingFailed(ClientHandler hnd,Message msg) {
        //may be overridden
    }
    
    protected void onClientConnected(ClientHandler hnd){
        //may be overridden
    }
    
    protected void onClientDisconnected(ClientHandler hnd){
        //may be overridden
    }
    
    /**
     * This method is called when a call to server's stop() method is made. 
     * This method is executed at the beginning of the stop() method i.e. before taking any stopping action.
     * It must be overridden in the child class.
     */
    protected void onServerStopping() {
        //may be overridden
    }
    
    protected ClientHandler newClientHandler(Socket socket,Server server){
        return new ClientHandler(socket, server);
    }
    
    public void disableHeartbeat(){
        if(!running)heartbeat=false;
    }
    public boolean isHeartbeatOn(){
        return heartbeat;
    }
}
