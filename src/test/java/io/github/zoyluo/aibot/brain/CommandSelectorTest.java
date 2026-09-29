package io.github.zoyluo.aibot.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandSelectorTest {
    @Test
    void selectsModeAndBlueprintGrant() {
        assertEquals(new CommandSelector.Selection(CommandSelector.Action.GAMEMODE, "survival"),
                CommandSelector.parseSelection("{\"action\":\"gamemode\",\"value\":\"survival\"}")
                        .orElseThrow());
        assertEquals(CommandSelector.Action.GIVE_BLUEPRINT,
                CommandSelector.parseSelection("{\"action\":\"give_blueprint\",\"value\":\"\"}")
                        .orElseThrow().action());
    }

    @Test
    void rejectsUnsupportedCommandsAndArguments() {
        assertTrue(CommandSelector.parseSelection("{\"action\":\"op\",\"value\":\"player\"}").isEmpty());
        assertTrue(CommandSelector.parseSelection("{\"action\":\"gamemode\",\"value\":\"operator\"}").isEmpty());
        assertTrue(CommandSelector.parseSelection("{\"action\":\"none\",\"value\":\"\"}").isEmpty());
    }
}
