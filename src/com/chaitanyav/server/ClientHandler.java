package com.chaitanyav.server;

import com.chaitanyav.Constants;
import com.chaitanyav.Message;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Chaitanya V
 */
public class ClientHandler {    
    final private Socket socket;
    private ObjectInputStream inputStream=null;
    private ObjectOutputStream outputStream=null;
    private volatile boolean connected=true;
    private Server server;
    private AtomicBoolean isActive = new AtomicBoolean();
    private long disconnectTimeout=Constants.PING_INTERVAL;   //default 10 seconds i.e. 10000ms
    private long pongTimeout=Constants.PONG_TIMEOUT;   //ping latency in ms
    
    final private ExecutorService sequentialExecutor = Executors.newSingleThreadExecutor();
    
    
    
    //pingpong machanism
    
    //<editor-fold>
    private Thread connCheckerThread;
    //pingpong lock
    private final Object connCheckerLock = new Object();
    private Runnable r_connChecker = new Runnable() {
        @Override
        public void run(){
            while (ClientHandler.this.connected) {
                    if (isActive.get()) {       //If some data was received in the past disconnectTimeout ms
                        isActive.set(false);         //Then set it to false to check its TRUEness next time
                        try {
                            synchronized (connCheckerLock) {
                                long startTime = System.nanoTime();
                                long curTime = startTime;
                                do { // Used to handle spurious wakeups
                                    connCheckerLock.wait(disconnectTimeout - (curTime - startTime) / 1000000);   //waiting for 10000 ms
                                    curTime = System.nanoTime();
                                } while (curTime - startTime < disconnectTimeout*1000000L && !ClientHandler.this.isActive.get());
                            }
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {        //If no data has been received, then 
                        try {
                            //...msg to notify pinging client
                            System.out.println("[SERVER] Pinging "+socket);
                            sendDataEx(Constants.PING, "Are you alive?");       //send a PING to check if the client is alive or not
                            synchronized (connCheckerLock) {
                                long startTime = System.nanoTime();
                                long curTime = startTime;
                                while (curTime - startTime < pongTimeout*1000000L && !ClientHandler.this.isActive.get()) { // Used to handle spurious wakeups
                                    connCheckerLock.wait(pongTimeout);   //waiting for pong timeout
                                    curTime = System.nanoTime();
                                }
                            }
                            if (!isActive.get()) {
                                ClientHandler.this.connected = false;
                            }
                        } catch (SocketException ex) {
                            ClientHandler.this.connected = false;
                        } catch (InterruptedException | IOException ex) {
                            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                //...msg to notify disconnection
                System.out.println("[SERVER] Client Disconnected - "+socket);
            }
        
    };
    
    //</editor-fold>
    
    
    //reader thread
    private Thread readerThread;
    private Runnable r_reader = new Runnable(){
        @Override
        public void run(){
            while(connected){
                try {
                    if(socket.getInputStream().available()>0){
                        Message msg = (Message) inputStream.readObject();
                        System.out.println("[SERVER] Message. TAG = "+msg.getTag()+" | "+socket);
                        
                        isActive.set(true);
                        synchronized (connCheckerLock) {
                            connCheckerLock.notifyAll();
                        }
                        
                        if(msg.getTag().equals(Constants.PING)){    //if client is checking connection
                            server.executeAction(sequentialExecutor,()->{sendData(Constants.PONG, "Connection is alive!");}
                                    , true);
                        } else {    //if not connection checking message
                            Action action = server.getAction(msg.getTag());
                            if (action != null) {
                                
                                //execute action                                
                                server.executeAction(sequentialExecutor,() -> {
                                    action.execute(ClientHandler.this, msg);
                                },action.requiresSeperateThread());

                            } //else ignore
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ClassNotFoundException ex) {
                    //ignore the message
                }
            }
        }
    };
    
    
    public ClientHandler(Socket socket,Server server){
        this.socket=socket;
        try{
            this.server=server;
            disconnectTimeout=this.server.getPingInterval();
            pongTimeout=this.server.getPongTimeout();
            
            outputStream=new ObjectOutputStream(socket.getOutputStream());
            inputStream=new ObjectInputStream(socket.getInputStream());
            isActive.set(true);
            connected=true;
            
            readerThread=new Thread(r_reader);
            readerThread.setName("ReaderThread");
            connCheckerThread = new Thread(r_connChecker);
            connCheckerThread.setName("ConnCheckerThread");
            readerThread.start();
            connCheckerThread.start();
            server.onClientConnected(this);
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
            stop();
        }
    }
    
    
    final public void stop(){
        connected=false;
        try {
            readerThread.join();
            synchronized(connCheckerLock){
                connCheckerLock.notifyAll();
            }
            connCheckerThread.join();
            socket.close();
        } catch (InterruptedException | IOException ex) {
        }
        
        sequentialExecutor.shutdown();
        if(server.isRunning()){
            server.onClientDisconnected(this);
            server.getClientList().remove(this);
        }
    }
    
    final public void stopNonBlocking(){
        connected = false;
        try {
            socket.close();
        } catch (IOException ex) {
        }
        
        sequentialExecutor.shutdown();
        if (server.isRunning()) {
            server.onClientDisconnected(this);
            server.getClientList().remove(this);
        }
    }
    
    /*TWO Synchronized methods to send data to client*/
    /**
     * This method is used to send some data in form of Message object, to the client.
     * In case of an IOException occurring while trying to write data to the client stream,
     * this method throws that exception and neither does calls onMsgSendingFailed as opposed to
     * sendData method.
     * Use - To deal with the IOException and message sending failure yourself.
     * @param tag
     * @param data
     * @param msg
     * @throws IOException 
     */
    public final void sendDataEx(String tag, Object data) throws IOException {
        Message msg = new Message(tag, data);
        synchronized(outputStream){
            outputStream.writeObject(msg);
        }
    }
    
    /**
     * This method is used to send some data in form of Message object, to the client.
     * If an IOException occurs while writing data to the stream, the client is considered
     * to be disconnected and this method then makes a call to onMsgSendingFailed and then 
     * stops the ClientHandler for the client.
     * To deal with the SocketException yourself, use the method sendDataEx which throws the SocketException
     * @param tag
     * @param data
     * @param msg The Message object containing the message to be sent to the client
     */
    public final void sendData(String tag, Object data) {
        Message msg = new Message(tag, data);
        synchronized(outputStream){
            try {
                outputStream.writeObject(msg);
            } catch (SocketException ex) {
                server.onMsgSendingFailed(ClientHandler.this,msg);
                stop();
            } catch (IOException ex) {
                Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
        } 
    }
    
    
    protected final void spuriousSafeSleep(int ms) throws InterruptedException{
        long startTime = System.nanoTime();
        long curTime = startTime;
        while((curTime-startTime)/1000000<ms){
            Thread.sleep(ms-((curTime-startTime)/1000000));   
            curTime=System.nanoTime();
        }
        
    }
    
}
