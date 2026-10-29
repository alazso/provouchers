package so.alaz.provouchers.comficonf;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class Yaml {

    private Yaml() {
    }

    /**
     * Parses YAML bytes with comments kept, so the document can be saved again without losing them. A
     * {@link YamlConfiguration} is not thread-safe: every callback parses its own.
     *
     * <p>Decoding is strict. A lossy decoder would turn every byte that is not UTF-8 into U+FFFD, and saving
     * would then write those replacement characters over the comments and values they came from.
     *
     * @throws IOException when the bytes are not UTF-8
     */
    static YamlConfiguration parse(byte[] content) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(decode(content));
        return yaml;
    }

    private static String decode(byte[] content) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("config.yml is not UTF-8 text. Save it as UTF-8 and try again.", e);
        }
    }

    /**
     * Encodes an emitted document with the line endings of the file it replaces. YAML emission always writes LF,
     * which would otherwise rewrite every line of a file saved with CRLF.
     */
    static byte[] encodeLike(String yaml, byte[] original) {
        String text = usesCarriageReturns(original) ? yaml.replace("\r\n", "\n").replace("\n", "\r\n") : yaml;
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether most of the line breaks in {@code content} are CRLF. */
    static boolean usesCarriageReturns(byte[] content) {
        int breaks = 0;
        int carriageReturns = 0;
        for (int i = 0; i < content.length; i++) {
            if (content[i] == '\n') {
                breaks++;
                if (i > 0 && content[i - 1] == '\r') {
                    carriageReturns++;
                }
            }
        }
        return carriageReturns * 2 > breaks;
    }
}
