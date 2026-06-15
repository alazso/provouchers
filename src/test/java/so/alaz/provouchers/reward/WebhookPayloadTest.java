package so.alaz.provouchers.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadTest {

    @Test
    void resolvesStringsAndPreservesStructureAndTypes() {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "Rare!");
        embed.put("description", "%player% won");
        embed.put("color", 16766720);
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("username", "Crate Bot");
        template.put("content", "hi %player%");
        template.put("embeds", List.of(embed));

        String json = WebhookPayload.render(template, s -> s.replace("%player%", "Steve"));

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertThat(root.get("username").getAsString()).isEqualTo("Crate Bot");
        assertThat(root.get("content").getAsString()).isEqualTo("hi Steve");
        JsonObject parsedEmbed = root.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertThat(parsedEmbed.get("description").getAsString()).isEqualTo("Steve won");
        assertThat(parsedEmbed.get("color").getAsInt()).isEqualTo(16766720);   // stays a number
    }

    @Test
    void escapesResolvedValuesSoTheyCannotInjectJson() {
        String injected = "evil\",\"x\":\"y";
        Map<String, Object> template = Map.of("content", "%arg%");

        String json = WebhookPayload.render(template, s -> s.replace("%arg%", injected));

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertThat(root.get("content").getAsString()).isEqualTo(injected);
        assertThat(root.has("x")).isFalse();
        assertThat(root.keySet()).containsExactly("content");
    }
}
