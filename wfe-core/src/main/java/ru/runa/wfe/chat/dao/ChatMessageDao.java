package ru.runa.wfe.chat.dao;

import java.util.Date;
import java.util.List;
import java.util.Set;
import net.bull.javamelody.MonitoredWithSpring;
import org.springframework.stereotype.Component;
import ru.runa.wfe.chat.ChatMessage;
import ru.runa.wfe.chat.ChatMessageRecipient;
import ru.runa.wfe.chat.QChatMessage;
import ru.runa.wfe.chat.QChatMessageRecipient;
import ru.runa.wfe.commons.dao.GenericDao;
import ru.runa.wfe.user.Actor;
import ru.runa.wfe.user.QActor;

@Component
@MonitoredWithSpring
public class ChatMessageDao extends GenericDao<ChatMessage> {

    public List<Actor> getRecipientsByMessageId(Long messageId) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        return queryFactory.select(cr.executor.as(QActor.class)).from(cr).where(cr.message.id.eq(messageId)).fetch();
    }

    public void readMessages(Actor user, List<ChatMessage> messages) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        Date date = new Date();
        queryFactory.update(cr).set(cr.readDate, date)
                .where(cr.executor.eq(user).and(cr.message.in(messages)).and(cr.readDate.isNull())).execute();
    }

    public List<ChatMessage> getMessages(Actor user, Long processId) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        return queryFactory.select(cr.message).from(cr)
                .where(cr.message.process.id.eq(processId).and(cr.executor.eq(user)))
                .orderBy(cr.message.createDate.desc()).fetch();
    }

    public Long getNewMessagesCount(Actor user) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        return queryFactory.select(cr.count()).from(cr).where(cr.executor.eq(user).and(cr.readDate.isNull())).fetchCount();
    }

    public List<ChatMessage> getNewMessagesByActor(Actor actor) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        QChatMessage cm = QChatMessage.chatMessage;
        return queryFactory.select(cm).from(cr).join(cr.message, cm).where(cr.executor.eq(actor).and(cr.readDate.isNull()))
                .orderBy(cm.createDate.desc()).fetch();
    }

    public ChatMessage save(ChatMessage message, Set<Actor> recipients) {
        ChatMessage result = create(message);
        for (Actor recipient : recipients) {
            sessionFactory.getCurrentSession().save(new ChatMessageRecipient(message, recipient));
        }
        return result;
    }

    public void deleteMessageAndRecipient(Long id) {
        QChatMessageRecipient cr = QChatMessageRecipient.chatMessageRecipient;
        queryFactory.delete(cr).where(cr.message.id.eq(id)).execute();
        delete(id);
    }

    public void deleteMessages(Long processId) {
        QChatMessage m = QChatMessage.chatMessage;
        for (ChatMessage cm : queryFactory.selectFrom(m).where(m.process.id.eq(processId)).fetch()) {
            deleteMessageAndRecipient(cm.getId());
        }
    }

    public List<ChatMessage> getByProcessId(long processId) {
        final QChatMessage message = QChatMessage.chatMessage;
        return queryFactory.select(message).from(message).where(message.process.id.eq(processId)).fetch();
    }
}