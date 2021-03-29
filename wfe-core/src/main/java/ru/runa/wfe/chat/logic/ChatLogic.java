package ru.runa.wfe.chat.logic;

import com.google.common.base.Joiner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.bull.javamelody.MonitoredWithSpring;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.runa.wfe.chat.ChatMessage;
import ru.runa.wfe.chat.ChatMessageFile;
import ru.runa.wfe.chat.UnreadMessagesPresentation;
import ru.runa.wfe.chat.dao.ChatFileIo;
import ru.runa.wfe.chat.dao.ChatMessageDao;
import ru.runa.wfe.chat.dto.ChatMessageFileDto;
import ru.runa.wfe.chat.dto.WfChatRoom;
import ru.runa.wfe.chat.dto.broadcast.MessageAddedBroadcast;
import ru.runa.wfe.chat.mapper.ChatMessageFileDetailMapper;
import ru.runa.wfe.chat.mapper.MessageAddedBroadcastFileMapper;
import ru.runa.wfe.chat.mapper.MessageAddedBroadcastMapper;
import ru.runa.wfe.commons.ClassLoaderUtil;
import ru.runa.wfe.commons.logic.WfCommonLogic;
import ru.runa.wfe.execution.Process;
import ru.runa.wfe.execution.logic.ExecutionLogic;
import ru.runa.wfe.presentation.BatchPresentation;
import ru.runa.wfe.security.AuthorizationException;
import ru.runa.wfe.security.Permission;
import ru.runa.wfe.security.SecuredObjectType;
import ru.runa.wfe.user.Actor;
import ru.runa.wfe.user.Executor;
import ru.runa.wfe.user.User;
import ru.runa.wfe.var.Variable;
import ru.runa.wfe.var.dto.WfVariable;

@MonitoredWithSpring
public class ChatLogic extends WfCommonLogic {
    private final Properties properties = ClassLoaderUtil.getProperties("chat.email.properties", false);
    @Autowired
    private ExecutionLogic executionLogic;
    @Autowired
    private ChatMessageDao messageDao;
    @Autowired
    private MessageAddedBroadcastMapper messageMapper;
    @Autowired
    private MessageAddedBroadcastFileMapper messageFileMapper;
    @Autowired
    private ChatMessageFileDetailMapper fileDetailMapper;
    @Autowired
    private ChatFileIo fileIo;
    @Autowired
    private MessageTransactionWrapper messageTransactionWrapper;

    public MessageAddedBroadcast saveMessage(User user, Long processId, ChatMessage message, Set<Actor> recipients) {
        final ChatMessage savedMessage = messageTransactionWrapper.save(message, recipients, processId);
        return messageMapper.toDto(savedMessage);
    }

    public MessageAddedBroadcast saveMessage(User user, Long processId, ChatMessage message, Set<Actor> recipients, List<ChatMessageFileDto> files) {
        final List<ChatMessageFile> savedFiles = fileIo.save(files);
        try {
            final ChatMessage savedMessage = messageTransactionWrapper.save(message, recipients, savedFiles, processId);
            final MessageAddedBroadcast broadcast = messageMapper.toDto(savedMessage);
            broadcast.setFiles(fileDetailMapper.toDtos(savedFiles));
            return broadcast;
        } catch (Exception exception) {
            fileIo.delete(savedFiles);
            throw exception;
        }
    }

    public List<Long> getRecipientIdsByMessageId(User user, Long messageId) {
        return messageDao.getRecipientIdsByMessageId(messageId);
    }

    @Transactional
    public void readMessage(User user, Long messageId) {
        messageDao.readMessage(user.getActor(), messageId);
    }

    public ChatMessage getMessageById(User user, Long messageId) {
        return messageDao.get(messageId);
    }

    @Transactional
    public List<MessageAddedBroadcast> getMessages(User user, Long processId) {
        List<ChatMessage> messages = messageDao.getMessages(user.getActor(), processId);
        if (!messages.isEmpty()) {
            messageDao.readMessage(user.getActor(), messages.get(0).getId());
        }
        return messageFileMapper.toDtos(messages);
    }

    public List<WfChatRoom> getChatRooms(User user, BatchPresentation batchPresentation) {
        if (batchPresentation == null) {
            return messageDao.getChatRooms(user.getActor());
        }
        List<String> additionalClauses = Arrays.asList(UnreadMessagesPresentation.NUMBER_OF_UNREAD_MESSAGES_FORMULA, "deployment2_.NAME",
                "deployment2_.VERSION", UnreadMessagesPresentation.UNREAD_MESSAGES_EXECUTOR_ID + "=" + user.getActor().getId());
        List<Process> orderedProcesses = getDistinctPersistentObjects(user, batchPresentation, Permission.READ,
                new SecuredObjectType[]{SecuredObjectType.CHAT_ROOMS}, true, additionalClauses);
        List<String> variableNamesToInclude = batchPresentation.getDynamicFieldsToDisplay(true);
        Map<Process, Map<String, Variable<?>>> variables = variableDao.getVariables(Sets.newHashSet(orderedProcesses), variableNamesToInclude);
        Map<Long, WfChatRoom> processIdToChatRoom = messageDao.getProcessIdToChatRoom(user.getActor(), orderedProcesses);
        List<WfChatRoom> rooms = Lists.newArrayListWithExpectedSize(orderedProcesses.size());
        for (Process process : orderedProcesses) {
            WfChatRoom room = processIdToChatRoom.get(process.getId());
            if (room != null) {
                for (WfVariable variable : executionLogic.getVariables(variableNamesToInclude, variables, process)) {
                    room.addVariable(variable);
                }
                rooms.add(room);
            }
        }
        return rooms;
    }

    public void deleteMessage(User user, Long messageId) {
        fileIo.delete(messageTransactionWrapper.delete(user, messageId));
    }

    public void updateMessage(User user, ChatMessage message) {
        if (!message.getCreateActor().equals(user.getActor())) {
            throw new AuthorizationException("Allowed for author only");
        }
        messageDao.update(message);
    }

    public void sendNotifications(User user, ChatMessage chatMessage, Collection<Executor> executors) {
        if (properties.isEmpty()) {
            log.debug("chat.email.properties are not defined");
            return;
        }
        try {
            Set<String> emails = new HashSet<String>();
            for (Executor executor : executors) {
                if (executor instanceof Actor && StringUtils.isNotBlank(((Actor) executor).getEmail())) {
                    emails.add(((Actor) executor).getEmail());
                }
            }
            if (emails.isEmpty()) {
                log.debug("No emails found for " + chatMessage);
                return;
            }
            javax.mail.Session session = javax.mail.Session.getDefaultInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(properties.getProperty("login"), properties.getProperty("password"));
                }
            });
            Message mimeMessage = new MimeMessage(session);
            String titlePattern = (String) properties.get("title.pattern");
            String title = titlePattern//
                    .replace("$actorName", chatMessage.getCreateActor().getName())//
                    .replace("$processId", chatMessage.getProcess().getId().toString());
            String message = ((String) properties.get("message.pattern")).replace("$message", chatMessage.getText());
            mimeMessage.setFrom(new InternetAddress(properties.getProperty("login")));
            mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(Joiner.on(";").join(emails)));
            mimeMessage.setSubject(title);
            mimeMessage.setText(message);
            Transport.send(mimeMessage);
        } catch (Exception e) {
            log.warn("Unable to send chat email notification", e);
        }
    }
}
