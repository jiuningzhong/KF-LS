package edu.cmu.side.model.data;

import java.io.Serializable;

public class ResponseJson implements Serializable
{
	private static final long serialVersionUID = -5433699826930815885L;
    String requestID;
    String predicted;
    String level;
    String accuracy;

    public ResponseJson(){

    }

    public ResponseJson(String requestID, String predicted, String level, String accuracy){
        this.requestID = requestID;
        this.level = level;
        this.predicted = predicted;
        this.accuracy = accuracy;
    }

    public void setRequestID(String requestID)
    {
        this.requestID = requestID;
    }

    public String getRequestID(){
        return this.requestID;
    }

    public void setPredicted(String predicted)
    {
        this.predicted = predicted;
    }

    public String getPredicted(){
        return this.predicted;
    }

    public void setAccuracy(String accuracy)
    {
        this.accuracy = accuracy;
    }

    public String getAccuracy(){
        return this.accuracy;
    }

    public String getLevel(){
        return this.level;
    }
    
    public void setLevel(String level)
    {
        this.level = level;
    }
}