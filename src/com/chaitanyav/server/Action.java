package com.chaitanyav.server;

import com.chaitanyav.Message;

/**
 *
 * @author Chaitanya V
 */
public abstract class Action{
    private boolean inSeperateThread=false;
    public Action(boolean inNewThread){
        this.inSeperateThread = inNewThread;
    }
    public Action(){
        this.inSeperateThread = true;
    }
    public boolean requiresSeperateThread(){
        return inSeperateThread;
    }
    
    public abstract void execute(ClientHandler hnd, Message msg);
}
