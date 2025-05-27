package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import common.Constants;

/**
 * Manages the persistent connection and state for both
 * download and comment‐approval handshakes.
 */
public class ServerConnection {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private String clientId = "clientID_placeholder";
    private String languagePref = "en";

    // ── Download handshake state ─────────────────────────
    private String pendingAskPayload;
    public synchronized void queuePendingAsk(String p){ pendingAskPayload=p; }
    public synchronized String consumePendingAsk(){
        String t=pendingAskPayload; pendingAskPayload=null; return t; }
    public synchronized boolean hasPendingAsk(){ return pendingAskPayload!=null; }

    enum RetryState { NONE, AWAIT_RETRY_CONFIRM, AWAIT_SAME_CONFIRM, AWAIT_SELECTION }
    private RetryState retryState = RetryState.NONE;
    private List<String> retryOwners = new ArrayList<>();
    private String retryLang, retryFile;

    public synchronized void initRetry(List<String> owners,String lang,String file){
        retryOwners.clear(); retryOwners.addAll(owners);
        retryLang=lang; retryFile=file;
        retryState=RetryState.AWAIT_RETRY_CONFIRM;
    }
    public synchronized RetryState getRetryState(){return retryState;}
    public synchronized List<String> getRetryOwners(){return retryOwners;}
    public synchronized String getRetryLang(){return retryLang;}
    public synchronized String getRetryFile(){return retryFile;}
    public synchronized void setRetryState(RetryState s){retryState=s;}
    public synchronized void clearRetry(){
        retryState=RetryState.NONE; retryOwners.clear();
        retryLang=retryFile=null;
    }

    // ── Comment‐approval handshake state ─────────────────
    private String pendingCommentAskPayload;
    public synchronized void queuePendingCommentAsk(String p){
        pendingCommentAskPayload=p;
    }
    public synchronized String consumePendingCommentAsk(){
        String t=pendingCommentAskPayload; pendingCommentAskPayload=null; return t;
    }
    public synchronized boolean hasPendingCommentAsk(){
        return pendingCommentAskPayload!=null;
    }

    public boolean connect(){
        try{
            socket=new Socket("localhost", Constants.SERVER_PORT);
            output=new ObjectOutputStream(socket.getOutputStream());
            input=new ObjectInputStream(socket.getInputStream());
            new Thread(new ServerListener(input,this)).start();
            return true;
        }catch(IOException e){
            System.out.println("Connection error: "+e.getMessage());
            return false;
        }
    }

    public void sendMessage(common.Message msg){
        try{ output.writeObject(msg); output.flush(); }
        catch(IOException e){ System.out.println("Error sending: "+e.getMessage()); }
    }

    public String getClientId(){return clientId;}
    public void setClientId(String id){clientId=id;}
    public String getLanguagePref(){return languagePref;}
    public void setLanguagePref(String lang){languagePref=lang;}
}
