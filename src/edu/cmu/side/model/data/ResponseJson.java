package edu.cmu.side.model.data;

import java.io.Serializable;

public class ResponseJson implements Serializable {
	private static final long serialVersionUID = -5433699826930815885L;
	private String requestID;
	private String predicted;
	private String level;
	private String accuracy;
	private String key1;
	private String key2;
	private String predictionConfidence1;
	private String predictionConfidence2;

	/*
	 * store data on the analytics tool side re. 1. who requested the feedback,
	 * when, 2. for which note (note ID, note text with scaffolds), 3.
	 * classification results generated from the tool, feedback given to the user.
	 */

	private String requesterName;
	private String requestTimestamp;

	private String noteText;
	private String noteID;

	private String feedbackText;

	public ResponseJson() {

	}

	public ResponseJson(String requestID, String predicted, String level, String accuracy, String key1, String key2,
			String predictionConfidence1, String predictionConfidence2) {
		super();
		this.requestID = requestID;
		this.predicted = predicted;
		this.level = level;
		this.accuracy = accuracy;
		this.key1 = key1;
		this.key2 = key2;
		this.predictionConfidence1 = predictionConfidence1;
		this.predictionConfidence2 = predictionConfidence2;
	}

	public ResponseJson(String requestID, String predicted, String level, String accuracy, String key1, String key2,
			String predictionConfidence1, String predictionConfidence2, String requesterName, String requestTimestamp,
			String noteText, String noteID, String feedbackText) {
		this.requestID = requestID;
		this.level = level;
		this.predicted = predicted;
		this.accuracy = accuracy;
		this.key1 = key1;
		this.key2 = key2;
		this.predictionConfidence1 = predictionConfidence1;
		this.predictionConfidence2 = predictionConfidence2;

		this.requesterName = requesterName;
		this.requestTimestamp = requestTimestamp;
		this.noteText = noteText;
		this.noteID = noteID;

		this.feedbackText = feedbackText;
	}

	public void setFeedbackTextByPredicted(String predicted) {
		switch (predicted) {
		case "L-R":
			this.feedbackText = "";
			break;
		case "L-RF":
			this.feedbackText = "Thanks for sharing this resource. \n" + 
			"-Can you say more? Why is this resource useful for our knowledge building?";
			break;
		case "L-RS":
			this.feedbackText = "Thank you for sharing this piece of our puzzle. Keep thinking and posting!";
			break;
		case "L-Q":
			this.feedbackText = "";
			break;
		case "L-QF":
			this.feedbackText = "Good start with your wondering!\n" + 
			"-Can you say more about your question or thought?";
			break;
		case "L-QS":
			this.feedbackText = "Good job! Keep researching and sharing your ideas!";
			break;
		case "L-I":
			this.feedbackText = "";
			break;
		case "L-F":
			this.feedbackText = "You are sharing some interesting information.  \n" + 
			"-Can you add more details or say more about what this means?";
			break;
		case "L-EF":
			this.feedbackText = "Thanks for sharing this interesting information.  \n" + 
			"-Can you explain how this exactly works, and why?";
			break;
		case "L-E":
			this.feedbackText = "This looks like an interesting idea. \n" + 
			"-	Can you say more?";
			break;
		case "L-EE":
			this.feedbackText = "Good job!  This looks like a great note! Encourage your peers to read it. Think about what you need to further research.";
			break;
		default:
			this.feedbackText = "Insufficient data. Please write more.";
		}
	}

	public String getRequesterName() {
		return requesterName;
	}

	public void setRequesterName(String requesterName) {
		this.requesterName = requesterName;
	}

	public String getRequestTimestamp() {
		return requestTimestamp;
	}

	public void setRequestTimestamp(String requestTimestamp) {
		this.requestTimestamp = requestTimestamp;
	}

	public String getNoteText() {
		return noteText;
	}

	public void setNoteText(String noteText) {
		this.noteText = noteText;
	}

	public String getNoteID() {
		return noteID;
	}

	public void setNoteID(String noteID) {
		this.noteID = noteID;
	}

	public String getFeedbackText() {
		return feedbackText;
	}

	public void setFeedbackText(String feedbackText) {
		this.feedbackText = feedbackText;
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

	public void setRequestID(String requestID) {
		this.requestID = requestID;
	}

	public String getRequestID() {
		return this.requestID;
	}

	public void setPredicted(String predicted) {
		this.predicted = predicted;
	}

	public String getPredicted() {
		return this.predicted;
	}

	public void setAccuracy(String accuracy) {
		this.accuracy = accuracy;
	}

	public String getAccuracy() {
		return this.accuracy;
	}

	public String getLevel() {
		return this.level;
	}

	public void setLevel(String level) {
		this.level = level;
	}
}