package com.chaitanyav.client;

import com.chaitanyav.Constants;
import com.chaitanyav.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author Chaitanya V
 */
public class Client {
    private HashMap<String, Action> actions = new HashMap<>();
    
    private int connCheckTimeout_ms=10000;
    private int pongTimeout_ms=500;
    
    private Socket socket;
    private String hostname="";
    private int port;
    private ObjectInputStream inputStream = null;
    private ObjectOutputStream outputStream = null;
    final private Object outputStreamLock = new Object();

    AtomicBoolean isAlive = new AtomicBoolean(false);
    
    private volatile boolean connected=false;
    private volatile boolean autoconnect=false;
    private int maxTries = 0;
    
    private Thread readerThread,connCheckThread;
    private Runnable readerRunnable = new Runnable(){
        @Override
        public void run(){
            while(connected){
                try {
                    if(socket.getInputStream().available()>0){
                        isAlive.set(true);
                        Message msg = (Message) inputStream.readObject();
                        String tag = msg.getTag();
                        System.out.println("[CLIENT] Msg from server - TAG = "+tag);
                        if(tag.equals(Constants.PING)){
                            sendData(new Message(Constants.PONG,"I am connected!"));
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
                    spuriousSafeSleep(connCheckTimeout_ms);
                    sendDataEx(new Message(Constants.PING, "Are you alive?"));
                    spuriousSafeSleep(pongTimeout_ms);
                    System.out.println("[CLIENT] Pong timeout");
                    disconnected();                    
                } catch (InterruptedException ex) {
                    isAlive.set(false);
                    if(connected==false){
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
    
    public Client(String hostname,int port,int connCheckTimeout_ms){
        this.hostname=hostname;
        this.port=port;
        this.connCheckTimeout_ms = connCheckTimeout_ms;
    }
    
    public void setToAutoConnect(int maxTries){
        this.maxTries=maxTries;
        if(maxTries!=0)autoconnect=true;
    }
    
    public void connect(){
        try {
            System.out.println("[CLIENT] Connecting to server...");
            connectEx();
            System.out.println("[CLIENT] Connected to server");
        } catch (IOException ex) {
            disconnected();
        }
    }
    
    private void connectEx() throws IOException{
        if(connected)return;
        socket = new Socket(hostname, port);
        
        synchronized(outputStreamLock){
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());            
        }
        connected=true;
        onConnect();

        readerThread = new Thread(readerRunnable);
        connCheckThread = new Thread(connCheckRunnable);
        readerThread.start();
        connCheckThread.start();
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
    
    public void sendDataEx(Message msg) throws IOException {
        if(!connected){
            onMsgSendingFailed(msg);
            return;
        }
        synchronized(outputStreamLock){
            outputStream.writeObject(msg);
        }
    }
    
    public void sendData(Message msg) {
        if(!connected){
            onMsgSendingFailed(msg);
            return;
        }
        synchronized(outputStreamLock){
            try {
                outputStream.writeObject(msg);
            } catch (SocketException ex) {
                onMsgSendingFailed(msg);
                connected=false;
                connCheckThread.interrupt();

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
        System.out.println("[CLIENT] Disconnected...");
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
                    System.out.println("[CLIENT] Reconnecting...");
                    connectEx();
                    break;
                } catch (IOException ex) {
                    System.out.println("[CLIENT] Failed to reconnect | Tries - "+(tries+1));
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
        System.out.println("[CLIENT] Stopping client...");
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
        System.out.println("[CLIENT] Client Stopped!");
    }
    
    public boolean isConnected(){
        return connected;
    }
    
    public int getLocalPort(){
        return socket.getLocalPort();
    }
    
}
