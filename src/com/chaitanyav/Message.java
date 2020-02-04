package com.chaitanyav;

import java.io.Serializable;

/**
 *
 * @author Chaitanya V
 */
public class Message implements Serializable{
    private String tag;
    private Object data;
    public Message(String tag,Object data){
        this.tag=tag;
        this.data=data;
    }
    public Object getData(){
        return data;
    }
    public String getTag(){
        return tag;
    }
}
