package com.chaitanyav.client;

import com.chaitanyav.Constants;
import static com.chaitanyav.Utils.*;
import com.chaitanyav.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Chaitanya V
 */
public class Client {
    private HashMap<String, Action> actions = new HashMap<>();
    
    private int pingInterval=Constants.PING_INTERVAL;
    private int pongTimeout=Constants.PONG_TIMEOUT;
    
    private Socket socket;
    private String hostname="";
    private int port;
    private ObjectInputStream inputStream = null;
    private ObjectOutputStream outputStream = null;
    final private Object outputStreamLock = new Object();

    AtomicBoolean isAlive = new AtomicBoolean(false);
    private boolean heartbeat = true;
    
    private volatile boolean connected=false;
    private volatile boolean autoconnect=false;
    private int maxTries = 0;
    
    private ReentrantLock callDisconnectedOnlyOnce = new ReentrantLock();
    private volatile boolean disconnectCalled=false;
    
    private Thread readerThread,connCheckThread;
    private Runnable readerRunnable = new Runnable(){
        @Override
        public void run(){
            while(connected){
                try {
                    if(socket.getInputStream().available()>0){
                        //isAlive.set(true);
                        connCheckThread.interrupt();
                        Message msg = (Message) inputStream.readObject();
                        String tag = msg.getTag();
                        log("[CLIENT] Msg from server - TAG = "+tag);
                        if(tag.equals(Constants.PING)){
                            sendData(Constants.PONG,"I am connected!");
                        } else if(tag.equals(Constants.PONG)){
                            connCheckThread.interrupt();
                        } else {                            
                            Action action = getAction(tag);
                            if(action!=null)action.execute(Client.this, msg);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ClassNotFoundException ex) {
                    //guarantee it will never occur
                }
            }
        }
    };
    
    Runnable connCheckRunnable = new Runnable() {
        @Override
        public void run() {
            while(connected){
                try {
                    spuriousSafeSleep(pingInterval);
                    sendDataEx(Constants.PING, "Are you alive?");
                    spuriousSafeSleep(pongTimeout);
                    log("[CLIENT] Pong timeout");
                    disconnected();                    
                } catch (InterruptedException ex) {
                    //isAlive.set(false);
                    if(connected==false){   //if connected is false it means some sendData method found an exception and thus the connection is broken
                        disconnected();
                        return;
                    }
                } catch (IOException ex) {
                    disconnected();
                    return;
                }
            }
        }
    };
    public Client(String hostname,int port){
        this(hostname, port, Constants.PING_INTERVAL, Constants.PONG_TIMEOUT);
    }
    public Client(String hostname,int port,int pingInterval,int pongTimeout){
        this.hostname=hostname;
        this.port=port;
        this.pingInterval = pingInterval;
        this.pongTimeout = pongTimeout;
    }
    
    public void setToAutoConnect(int maxTries){
        this.maxTries=maxTries;
        if(maxTries!=0)autoconnect=true;
    }
    
    public void start(){
        try {
            log("[CLIENT] Connecting to server...");
            startEx();
            log("[CLIENT] Connected to server");
        } catch (IOException ex) {
            disconnected();
        }
    }
    
    private void startEx() throws IOException{
        if(connected)return;
        socket = new Socket(hostname, port);
        
        synchronized(outputStreamLock){
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());            
        }
        connected=true;
        disconnectCalled=false;
        onConnect();

        readerThread = new Thread(readerRunnable);
        connCheckThread = new Thread(connCheckRunnable);
        readerThread.start();
        if(heartbeat)connCheckThread.start();
    }
    
    final public void setAction(String tag, Action action) throws Exception {
        if (tag.equals(Constants.PING) || tag.equals(Constants.PONG) || actions.containsKey(tag)) {
            throw new Exception("Action already exists!");
        } else {
            actions.put(tag, action);
        }
    }
    
    public final Action getAction(String tag){
        return actions.get(tag);
    }
    
    public void sendDataEx(String tag, Object data) throws IOException {
        Message msg = new Message(tag, data);
        if(!connected){
            onMsgSendingFailed(msg);
            return;
        }
        // else if connected, send message only then
        synchronized(outputStreamLock){
            outputStream.writeObject(msg);
        }
    }
    
    public void sendData(String tag, Object data) {
        Message msg = new Message(tag, data);
        if(!connected){
            onMsgSendingFailed(msg);
            return;
        }
        //else if connected, send message only then
        synchronized(outputStreamLock){
            try {
                outputStream.writeObject(msg);
            } catch (SocketException ex) {
                onMsgSendingFailed(msg);
                connected=false;
                connCheckThread.interrupt();
                if(!heartbeat){
                    //do this only when heartbeat is disabled i.e. no other thread
                    //exists to take care of disconnection
                    
                    //call the disconnected method only once from all threads
                    if(callDisconnectedOnlyOnce.tryLock()){//try obtaining a lock
                        //if lock was obtained, call disconnected() in another thread
                        new Thread(()->{disconnected();}).start();
                        //set the flag to true so as after releasing lock, if other
                        //thread acquire lock, they won't call this method again
                        //the flag is reset after reconnection
                        disconnectCalled = true;
                        callDisconnectedOnlyOnce.unlock();
                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            } 
        } 
    }
    
    
    
    private void spuriousSafeSleep(int ms) throws InterruptedException{
        long startTime = System.nanoTime();
        long curTime = startTime;
        while((curTime-startTime)/1000000<ms){
            Thread.sleep(ms-((curTime-startTime)/1000000));   
            curTime=System.nanoTime();
        }
        
    }
    
    protected void onMsgSendingFailed(Message msg){
        //must be overridden
    }
    
    protected void onDisconnect(){
        //must be overridden
    }
    
    protected void onConnect(){
        //must be overridden
    }
    
    private void disconnected(){
        //code to execute when client disconnects from the server
        log("[CLIENT] Disconnected...");
        onDisconnect();
        connected=false;
        try {
            if(readerThread!=null)readerThread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(autoconnect){
            for(int tries=0;(maxTries<0)||tries<maxTries;tries=(maxTries>0)?tries+1:tries){
                try {
                    log("[CLIENT] Reconnecting...");
                    startEx();
                    break;
                } catch (IOException ex) {
                    log("[CLIENT] Failed to reconnect | Tries - "+(tries+1));
                }
            }
        }
    }
    
    public void testDisconnect(){
        connected=false;
        connCheckThread.interrupt();
    }
    
    public final void stop(){
        //code to stop 
        log("[CLIENT] Stopping client...");
        boolean bkup=autoconnect;
        autoconnect=false;
        connected=false;
        connCheckThread.interrupt();
        try {
            readerThread.join();
            connCheckThread.join();
            socket.close();
        } catch (IOException ex) {
        } catch (InterruptedException ex) {
        }
        autoconnect=bkup;
        log("[CLIENT] Client Stopped!");
    }
    
    public boolean isConnected(){
        return connected;
    }
    
    public int getLocalPort(){
        return socket.getLocalPort();
    }
    
    public void disableHeartbeat(){
        heartbeat=false;
    }
            
}
