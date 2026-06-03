package uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.model.OutboundMessage;

@Slf4j
@Service
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class JournalService {

    private static final Map<String, List<OutboundMessage>> REQUEST_JOURNALS_MAP = new ConcurrentHashMap<>();

    public Map<String, List<OutboundMessage>> getRequestJournal() {
        return REQUEST_JOURNALS_MAP;
    }

    public Optional<List<OutboundMessage>> getRequestJournalById(String correlationId) {
         return Optional.ofNullable(REQUEST_JOURNALS_MAP.get(correlationId));
    }

    public void postJournalEntry(String correlationId, OutboundMessage message) {

        if(REQUEST_JOURNALS_MAP.containsKey(correlationId)) {
            REQUEST_JOURNALS_MAP.replace(correlationId, Stream.concat(
                    REQUEST_JOURNALS_MAP.get(correlationId).stream(),
                    Stream.of(message)
            ).collect(Collectors.toList()));

            LOGGER.info("Updated Journal entry id: {}" + correlationId);
        } else {
            REQUEST_JOURNALS_MAP.put(correlationId, List.of(message));

            LOGGER.info("Added new Journal entry with id: {}" + correlationId);
        }
    }



}
