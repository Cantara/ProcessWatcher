package no.cantara.llmweather.mcp;

/** A tool-level failure, reported to the client as a tool result with isError=true (not a protocol error). */
public final class ToolError extends Exception {
    public ToolError(String message) {
        super(message);
    }
}
