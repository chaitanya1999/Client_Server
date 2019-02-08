package com.chaitanyav;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Chaitanya V
 */
public class ClientHandler {
    Socket socket;
    ObjectInputStream inputStream=null;
    ObjectOutputStream outputStream=null;
    int serverListIndex;
    volatile boolean connected=true;
    Server server;
    AtomicBoolean isActive = new AtomicBoolean();
    long disconnectTimeout=10000;   //default 10 seconds i.e. 10000ms
    long latency=200;   //ping latency in ms
    
    //pingpong thread
    Thread connCheckerThread;
    //pingpong lock
    final Object connCheckerLock = new Object();
    Runnable r_connChecker = new Runnable() {
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
                            System.out.println("[CLIENT] Pinging "+socket);
                            sendData(new Message(Constants.PING, "Are you alive?"));       //send a PING to check if the client is alive or not
                            synchronized (connCheckerLock) {
                                long startTime = System.nanoTime();
                                long curTime = startTime;
                                while (curTime - startTime < latency*1000000L && !ClientHandler.this.isActive.get()) { // Used to handle spurious wakeups
                                    connCheckerLock.wait(latency);   //waiting for 1100 ms
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
                System.out.println("[CLIENT] Disconnected - "+socket);
            }
        
    };
    
    //reader thread
    Thread readerThread;
    Runnable r_reader = new Runnable(){
        @Override
        public void run(){
            while(connected){
                try {
                    if(socket.getInputStream().available()>0){
                        Message msg = (Message) inputStream.readObject();
                        System.out.println("[CLIENT] Message. TAG = "+msg.getTag()+" | "+socket);
                        
                        isActive.set(true);
                        synchronized (connCheckerLock) {
                            connCheckerLock.notifyAll();
                        }
                        
                        if(msg.getTag().equals(Constants.PONG)){    //if connection checker message
                            System.out.println("[CLIENT] Alive - "+socket);
                        } else {    //if not connection checking message
                            Action action = server.actions.get(msg.getTag());
                            if (action != null) {
                                //execute action
                                if (action.requiresSeperateThread()) {
                                    server.pool.execute(() -> {
                                        action.execute(ClientHandler.this, msg);
                                    });
                                } else {
                                    action.execute(ClientHandler.this, msg);
                                }
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
    
    
    public ClientHandler(Socket socket,Server server,int index){
        this.socket=socket;
        try{
            this.server=server;
            disconnectTimeout=this.server.disconnectTimeout;
            latency=this.server.latency;
            
            this.serverListIndex=index;
            inputStream=new ObjectInputStream(socket.getInputStream());
            outputStream=new ObjectOutputStream(socket.getOutputStream());
            isActive.set(true);
            connected=true;
            
            readerThread=new Thread(r_reader);
            connCheckerThread = new Thread(r_connChecker);
            readerThread.start();
            connCheckerThread.start();
        } catch (IOException ex) {
            Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
            try {
                stop();
            } catch (IOException ex1) {
                Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
    }
    
    
    public void stop() throws IOException{
        connected=false;
        socket.close();
        if(server.running)server.clientHandlers.remove(serverListIndex);
        //and other stop code
    }
    
    /*TWO Synchronized methods to send data to client*/
    public void sendData(Message msg) throws IOException {
        synchronized(outputStream){
            outputStream.writeObject(msg);
        }
    }
    public void sendDataHandleDisconnect(Message msg) {
        synchronized(outputStream){
            try {
                outputStream.writeObject(msg);
            } catch (SocketException ex) {
                try {
                    stop();
                } catch (IOException ex1) {
                    Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex1);
                }

            } catch (IOException ex) {
                Logger.getLogger(ClientHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
        } 
    }
    
}
