package uk.nhs.adaptors.pss.translator.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.io.IOUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class XmlUnmarshallUtil {

    private static final ConcurrentMap<Class<?>, JAXBContext> CONTEXT_CACHE = new ConcurrentHashMap<>();

    private static JAXBContext getContext(Class<?> clazz) {
        return CONTEXT_CACHE.computeIfAbsent(clazz, c -> {
            try {
                return JAXBContext.newInstance(c);
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static <T> T unmarshallFile(File xmlFile, Class<T> destinationClass) throws JAXBException {

        Unmarshaller unmarshaller = getContext(destinationClass).createUnmarshaller();
        JAXBElement<T> unmarshalledMessage = (JAXBElement) unmarshaller.unmarshal(xmlFile);
        return unmarshalledMessage.getValue();
    }


    public static <T> T unmarshallString(String xmlString, Class<T> destinationClass) throws JAXBException {
        Unmarshaller unmarshaller = createUnmarshaller(destinationClass);
        JAXBElement<T> unmarshalledMessage = (JAXBElement) unmarshaller.unmarshal(
            IOUtils.toInputStream(xmlString, UTF_8)
        );
        return unmarshalledMessage.getValue();
    }

    private static <T> Unmarshaller createUnmarshaller(Class<T> destinationClass) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(destinationClass);
        return jaxbContext.createUnmarshaller();
    }
}
