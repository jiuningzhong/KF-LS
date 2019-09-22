package edu.cmu.side.model.data;

import java.io.Serializable;

public class ResponseJson implements Serializable
{
	private static final long serialVersionUID = -5433699826930815885L;
    String requestID;
    String predicted;
    String level;
    String accuracy;
    String key1;
    String key2;
    String predictionConfidence1;
    String predictionConfidence2;

    public ResponseJson(){

    }

    public ResponseJson(String requestID, String predicted, String level, String accuracy, String key1, String key2, String predictionConfidence1, String predictionConfidence2){
        this.requestID = requestID;
        this.level = level;
        this.predicted = predicted;
        this.accuracy = accuracy;
        this.key1 = key1;
        this.key2 = key2;
        this.predictionConfidence1 = predictionConfidence1;
        this.predictionConfidence2 = predictionConfidence2;
    }   
    
    public String getKey1() {
		return key1;
	}

	public void setKey1(String key1) {
		this.key1 = key1;
	}

	public String getKey2() {
		return key2;
	}

	public void setKey2(String key2) {
		this.key2 = key2;
	}

	public String getPredictionConfidence2() {
		return predictionConfidence2;
	}

	public void setPredictionConfidence2(String predictionConfidence2) {
		this.predictionConfidence2 = predictionConfidence2;
	}

	public String getPredictionConfidence1() {
		return predictionConfidence1;
	}

	public void setPredictionConfidence1(String predictionConfidence1) {
		this.predictionConfidence1 = predictionConfidence1;
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