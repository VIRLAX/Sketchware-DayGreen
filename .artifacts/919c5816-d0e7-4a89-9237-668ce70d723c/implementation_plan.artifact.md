# Dayven AI Agent Overhaul Plan

This plan details a complete overhaul of the AI Agent UI and system to match a "Claude-like" premium experience with enhanced capabilities, multi-API management, and intelligent model routing.

## User Review Required

> [!IMPORTANT]
> The overhaul involves a significant change to the UI architecture of the Agent. We will move away from a simple programmatic layout to a more polished, responsive design with specialized components for chat bubbles, thinking states, and proposals.

> [!WARNING]
> The automated model routing will prioritize "Lite" (Flash) models for simple queries and "Pro" models for complex tasks to balance speed and intelligence. This requires at least one API key that supports these models.

## Proposed Changes

### AI Agent UI & Interaction

#### [MODIFY] [AgentFragmentV2.java](file:///C:/Users/only1/StudioProjects/Sketchware/app/src/main/java/pro/sketchware/editor/AgentFragmentV2.java)
- **Redesign UI**: Overhaul the `build` method to create a cleaner, more premium layout.
    - Softer color palette (Claude-like).
    - Polished chat bubbles with better typography and spacing.
    - Improved Header with real-time API status and token usage.
    - Responsive composer bar with better attachment and mic icons.
- **Thinking State**: Implement the specific "thinking" text and animation requested.
- **Formal Persona**: Update `GREETING` and system prompt to a professional "Dayven AI" teacher/student persona.
- **Model Routing**: Implement logic to automatically switch between Lite/Medium/Pro based on request complexity and API limits.
- **Multi-API Management**: Enhance the settings page to support adding, validating, and toggling multiple Gemini API keys.
- **Proposal System**: Refine the Accept/Cancel workflow for code changes.

#### [MODIFY] [AgentPromptOptimizer.java](file:///C:/Users/only1/StudioProjects/Sketchware/app/src/main/java/pro/sketchware/editor/AgentPromptOptimizer.java)
- **Aggressive Compaction**: Optimize prompt construction to save up to 80% of tokens by intelligently pruning non-relevant context.

#### [MODIFY] [AgentWorkspaceStore.java](file:///C:/Users/only1/StudioProjects/Sketchware/app/src/main/java/pro/sketchware/editor/AgentWorkspaceStore.java)
- **Session Persistence**: Ensure sessions are correctly managed and retrieved with the correct context.

### API & Connectivity

#### [MODIFY] [GeminiAutoClient.java](file:///C:/Users/only1/StudioProjects/Sketchware/app/src/main/java/pro/sketchware/editor/GeminiAutoClient.java)
- **Validation**: Improve API key validation to check for model availability and rate limits.
- **Heartbeat**: Implement a more robust connection monitoring system.

## Verification Plan

### Automated Tests
- Verify prompt optimization logic with various project sizes.
- Test model routing logic with different query complexities.

### Manual Verification
1. Open the new AI Agent interface and verify the "Claude-like" design.
2. Add multiple API keys in Settings, validate them, and verify the rotating logic if a limit is reached.
3. Send various requests (greetings, simple fixes, complex feature requests) and observe the auto-model routing.
4. Verify the "thinking" indicator and the final formal response.
5. Test the proposal system by asking the AI to modify a file and then clicking "Accept" or "Cancel".
6. Check the Hamburger menu for session history and "New Chat" functionality.
