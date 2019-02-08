package com.chaitanyav;

/**
 *
 * @author Chaitanya V
 */
public abstract class Action{
    boolean inSeperateThread=false;
    public Action(boolean inNewThread){
        this.inSeperateThread = inNewThread;
    }
    public Action(){
        this.inSeperateThread = false;
    }
    public boolean requiresSeperateThread(){
        return inSeperateThread;
    }
    
    public abstract void execute(ClientHandler hnd, Message msg);
}
