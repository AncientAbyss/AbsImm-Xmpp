package net.ancientabyss.absimm.xmpp;

import net.ancientabyss.absimm.core.ReactionClient;
import net.ancientabyss.absimm.core.Story;
import net.ancientabyss.absimm.core.StoryException;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class MyNewMessageListener implements ReactionClient, ChatMessageListener {

    private static final Logger LOG = Logger.getLogger(MyNewMessageListener.class.getCanonicalName());

    private Story story;
    private Chat chat;
    private boolean closed = false;
    private boolean firstMessage = true;
    private List<ReactionClient> additionalClients = new ArrayList<>();

    public MyNewMessageListener(Chat chat, Story story) {
        this.chat = chat;
        this.story = story;
        story.addClient(this);
        try {
            story.tell();
        } catch (StoryException | SmackException.NotConnectedException e) {
            LOG.severe("Failed telling story: " + e.getMessage());
        }
    }

    public void addAdditionialClient(ReactionClient client) {
        additionalClients.add(client);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        if (firstMessage) {
            firstMessage = false;
            return;
        }

        LOG.info(chat.getParticipant());
        LOG.info(message.getBody());
        try {
            if (message.getBody() != null && !message.getBody().isEmpty()) {
                if (message.getBody().equals(story.getSettings().getSetting("quit_command"))) {
                    quit();
                    return;
                }
                if (message.getBody().equals("admin_quit")) {  // TODO: extract generic admin command interface
                    closed = true;
                    return;
                }
                story.interact(message.getBody());
            }
        } catch (StoryException e) {
            try {
                sendMessageToAllClients(e.getMessage());
            } catch (XMPPException | SmackException.NotConnectedException e1) {
                LOG.severe(e.getMessage());
            }
            LOG.severe(e.getMessage());
        } catch (XMPPException | SmackException.NotConnectedException e) {
            LOG.severe(e.getMessage());
        }
    }

    public void quit() throws XMPPException, SmackException.NotConnectedException {
        // TODO: properly cleanup
        if (chat.getListeners().isEmpty()) return;

        sendMessageToAllClients(story.getSettings().getSetting("quit_message"));
        chat.removeMessageListener(this); // TODO: how to resume game in this chat session
    }

    @Override
    public void reaction(String text) throws SmackException.NotConnectedException {
        try {
            // TODO: the message is not delivered properly if sent as a whole in fb
            for (String part : text.split("\\\\n")) {
                Thread.sleep(2 * 100);
                LOG.info(": " + part);
                sendMessageToAllClients(part);
            }
        } catch (XMPPException | InterruptedException e) {
            LOG.severe(e.getMessage());
        }
    }

    private void sendMessageToAllClients(String message) throws XMPPException, SmackException.NotConnectedException {
        for (ReactionClient client : additionalClients) {
            client.reaction(message);
        }
        chat.sendMessage(message);
    }

    public boolean isClosed() {
        return closed;
    }
}

