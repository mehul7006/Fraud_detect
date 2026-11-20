package com.fraudshield.service;

import com.fraudshield.service.CarrierClassifier.Rule;
import com.fraudshield.service.CarrierClassifier.State;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure logic of the classifier (3.2). The seed-rule corpus test lives in ForwardingIT. */
class CarrierClassifierTest {

    @Test
    void lowerPriorityNumberWinsRegardlessOfListOrder() {
        var c = new CarrierClassifier(List.of(
                new Rule("ACTIVE", "\\d{10}", 30),
                new Rule("INACTIVE", "(?i)not forwarded", 10)));
        assertEquals(State.INACTIVE, c.classify("Not forwarded to 9876543210"));
        assertEquals(State.ACTIVE, c.classify("Forwarded to 9876543210"));
    }

    @Test
    void noMatchIsUnknown_andNullOrBlankIsUnknown() {
        var c = new CarrierClassifier(List.of(new Rule("ACTIVE", "\\d{10}", 1)));
        assertEquals(State.UNKNOWN, c.classify("Connection problem"));
        assertEquals(State.UNKNOWN, c.classify(null));
        assertEquals(State.UNKNOWN, c.classify("   "));
    }

    @Test
    void brokenOrOversizedRegexIsSkipped_notFatal() {
        var c = new CarrierClassifier(List.of(
                new Rule("ACTIVE", "([unclosed", 1),             // invalid regex
                new Rule("ACTIVE", "a".repeat(201), 2),          // longer than the 200 limit
                new Rule("BOGUS", "x", 3),                       // unknown kind
                new Rule("ACTIVE", "(?i)forwarded", 4)));        // the only usable rule
        assertEquals(1, c.ruleCount());
        assertEquals(State.ACTIVE, c.classify("Forwarded"));
    }

    @Test
    void onlyFirst500CharactersAreExamined() {
        var c = new CarrierClassifier(List.of(new Rule("ACTIVE", "SECRET", 1)));
        assertEquals(State.UNKNOWN, c.classify("x".repeat(600) + "SECRET"));
        assertEquals(State.ACTIVE, c.classify("x".repeat(100) + "SECRET"));
    }

    @Test
    void kindIsCaseInsensitive() {
        var c = new CarrierClassifier(List.of(new Rule("inactive", "off", 1)));
        assertEquals(State.INACTIVE, c.classify("off"));
    }
}
