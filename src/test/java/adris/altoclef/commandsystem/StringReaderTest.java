package adris.altoclef.commandsystem;

import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.CommandNotFinishedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringReaderTest {

    @Test
    void splitsOnSpacesAndTracksIndices() throws CommandException {
        StringReader reader = new StringReader("get diamond 3");

        assertEquals(3, reader.size());
        assertEquals(0, reader.getIndex());
        assertEquals("get", reader.next());
        assertEquals(4, reader.getIndex());
        assertEquals("diamond", reader.next());
        assertEquals(12, reader.getIndex());
        assertEquals("3", reader.next());
        assertFalse(reader.hasNext());
    }

    @Test
    void throwsWhenConsumed() throws CommandException {
        StringReader reader = new StringReader("a");
        reader.next();

        assertThrows(CommandNotFinishedException.class, reader::next);
        assertThrows(CommandNotFinishedException.class, reader::peek);
        assertEquals("", reader.nextOrEmpty());
    }

    @Test
    void peekDoesNotConsume() throws CommandException {
        StringReader reader = new StringReader("a b");

        assertEquals("a", reader.peek());
        assertEquals("a", reader.next());
    }

    @Test
    void nextCharConsumesCharacterWise() throws CommandException {
        StringReader reader = new StringReader("ab c");

        assertEquals('a', reader.nextChar());
        assertEquals('b', reader.nextChar());
        assertEquals("c", reader.next());
    }

    @Test
    void copyIsIndependentAndSetRestores() throws CommandException {
        StringReader reader = new StringReader("a b c");
        StringReader copy = reader.copy();

        reader.next();
        reader.next();
        assertEquals(3, copy.size());

        reader.set(copy);
        assertEquals("a", reader.next());
    }

    @Test
    void keepsEmptyTokensFromDoubleSpaces() throws CommandException {
        StringReader reader = new StringReader("a  b");

        assertEquals(3, reader.size());
        assertEquals("a", reader.next());
        assertEquals("", reader.next());
        assertEquals("b", reader.next());
    }
}
