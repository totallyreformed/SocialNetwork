package client;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import common.Message;
import common.Message.MessageType;
import common.Constants;
import common.Util;

/**
 * Listens for server messages and drives UI / download / comment‐approval flows.
 */
public class ServerListener implements Runnable {

    private final ObjectInputStream input;
    private final ServerConnection  connection;
    private ProfileClientManager    profileClientManager;

    // Download state:
    private List<String> lastOwners = new ArrayList<>();
    private String lastLang, lastDownloadFileName;

    public ServerListener(ObjectInputStream input, ServerConnection connection) {
        this.input      = input;
        this.connection = connection;
    }

    @Override
    public void run() {
        try {
            Message msg;
            while ((msg = (Message) input.readObject()) != null) {

                if (msg.getType() == MessageType.AUTH_SUCCESS) {
                    connection.setClientId(msg.getSenderId());
                    profileClientManager = new ProfileClientManager(msg.getSenderId());
                    System.out.println(msg.getPayload());
                    continue;
                }
                if (msg.getType() == MessageType.AUTH_FAILURE) {
                    System.out.println(msg.getPayload());
                    continue;
                }

                // ── Download handshake ──
                if (msg.getType() == MessageType.ASK) {
                    connection.queuePendingAsk(msg.getPayload());
                    System.out.println("\n>>> Download request pending. yes/no?");
                    continue;
                }
                if (msg.getType() == MessageType.PERMIT) {
                    handlePermit(msg);
                    continue;
                }
                if (msg.getType() == MessageType.DENY) {
                    handleDeny(msg);
                    continue;
                }

                // ── Comment‐approval handshake ──
                if (msg.getType() == MessageType.ASK_COMMENT) {
                    connection.queuePendingCommentAsk(msg.getPayload());
                    Map<String,String> m = Util.parsePayload(msg.getPayload());
                    String rid = m.get("requesterId"),
                            pid = m.get("postId"),
                            text = m.get("commentText");
                    System.out.println("\n>>> User '" + rid + "' comments on your post "
                            + pid + ": \"" + text + "\". yes/no?");
                    continue;
                }
                if (msg.getType() == MessageType.APPROVE_COMMENT) {
                    Map<String,String> m = Util.parsePayload(msg.getPayload());
                    String owner = m.get("ownerUsername"),
                            pid   = m.get("postId"),
                            text  = m.get("commentText");
                    String pay = "ownerUsername:" + owner
                            + "|postId:" + pid
                            + "|commentText:" + text;
                    connection.sendMessage(new Message(MessageType.COMMENT,
                            connection.getClientId(), pay));
                    System.out.println("Comment approved—publishing.");
                    continue;
                }
                if (msg.getType() == MessageType.DENY_COMMENT) {
                    System.out.println("Comment rejected by owner.");
                    continue;
                }

                // ── File transfer & other DIAGNOSTICs ──
                if (msg.getType() == MessageType.DIAGNOSTIC) {
                    String p = msg.getPayload();
                    if (p.startsWith("Search: found photo ")) {
                        System.out.println(p);
                        String[] parts = p.split(" at: ");
                        String raw = parts[0].substring("Search: found photo ".length());
                        int idx = raw.indexOf(" (");
                        String file = idx<0? raw : raw.substring(0, idx);
                        lastOwners.clear();
                        for (String tok : parts[1].split(",")) {
                            String nm = tok.substring(tok.indexOf('(')+1, tok.indexOf(')'));
                            lastOwners.add(nm);
                        }
                        lastDownloadFileName = file;
                        lastLang = connection.getLanguagePref();
                        String owner = lastOwners.get(
                                (int)(Math.random()*lastOwners.size()));
                        System.out.println("Initiating ASK to " + owner);
                        sendAsk(owner, file, lastLang);
                        continue;
                    }
                    if (p.startsWith("Caption: ")) {
                        saveCaptionFile(p.substring("Caption: ".length()));
                        continue;
                    }
                    if (p.equals("No caption available")) {
                        saveCaptionFile("");
                        continue;
                    }
                    System.out.println(p);
                }

                if (msg.getType()==MessageType.HANDSHAKE
                        || msg.getType()==MessageType.FILE_CHUNK
                        || msg.getType()==MessageType.FILE_END
                        || msg.getType()==MessageType.NACK) {
                    if (msg.getType()==MessageType.HANDSHAKE) {
                        String hs = msg.getPayload();
                        int idx = hs.indexOf("for ");
                        if (idx!=-1) lastDownloadFileName=hs.substring(idx+4).trim();
                    }
                    FileTransferHandler.handleIncomingMessage(msg, connection);
                }
            }
        } catch(IOException|ClassNotFoundException e) {
            System.out.println("Disconnected.");
        }
    }

    private void handlePermit(Message permit) {
        Map<String,String> m=Util.parsePayload(permit.getPayload());
        String owner=m.get("ownerUsername"),
                file =m.get("file"),
                lang =m.get("lang");
        lastDownloadFileName=file;
        String dl="lang:"+lang+"|ownerFilename:"+owner+":"+file;
        connection.sendMessage(new Message(MessageType.DOWNLOAD,
                connection.getClientId(), dl));
        FileTransferHandler.downloadFile(dl, connection);
    }

    private void handleDeny(Message deny) {
        System.out.println("Download denied – "+deny.getPayload());
        Map<String,String> m=Util.parsePayload(deny.getPayload());
        String file=m.get("file"), lang=m.get("lang"),
                owner=m.get("ownerUsername");
        List<String> owners = lastOwners.isEmpty()
                ? List.of(owner)
                : new ArrayList<>(lastOwners);
        connection.initRetry(owners, lang, file);
        System.out.print("Retry download? (yes/no): ");
    }

    private void sendAsk(String owner, String file, String lang) {
        String pay="requesterId:"+connection.getClientId()
                +"|ownerUsername:"+owner
                +"|file:"+file
                +"|lang:"+lang;
        connection.sendMessage(new Message(MessageType.ASK,
                connection.getClientId(), pay));
    }

    private void saveCaptionFile(String captionText) {
        if (lastDownloadFileName==null) {
            System.out.println("No filename recorded; skipping caption.");
            return;
        }
        try {
            String base=lastDownloadFileName;
            int dot=base.lastIndexOf('.');
            if(dot>0) base=base.substring(0,dot);
            String clientId=connection.getClientId();
            File dir=new File("ClientFiles/"+Constants.GROUP_ID
                    +"client"+clientId);
            if(!dir.exists()) dir.mkdirs();
            File cf=new File(dir, base+".txt");
            try(FileWriter fw=new FileWriter(cf)){
                fw.write(captionText);
            }
            System.out.println("Caption saved to "+cf.getPath());
        } catch(IOException e){
            System.out.println("Caption save error: "+e.getMessage());
        }
    }
}
