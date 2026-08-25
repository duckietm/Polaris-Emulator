package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

/**
 * Interface for wired triggers in the new context-driven architecture.
 * <p>
 * Triggers are the entry point for wired execution. They listen for specific
 * event types and determine whether to activate based on their configuration.
 * </p>
 * 
 * <h3>Lifecycle:</h3>
 * <ol>
 *   <li>Engine receives a {@link WiredEvent}</li>
 *   <li>Engine finds triggers that listen to that event type via {@link #listensTo()}</li>
 *   <li>Engine calls {@link #matches(HabboItem, WiredEvent)} for quick filtering</li>
 *   <li>If matched, conditions are evaluated and effects executed</li>
 * </ol>
 * 
 * <h3>Example Implementation:</h3>
 * <pre>{@code
 * public class UserSaysTrigger implements IWiredTrigger {
 *     private final String keyword;
 *     
 *     public WiredEvent.Type listensTo() {
 *         return WiredEvent.Type.USER_SAYS;
 *     }
 *     
 *     public boolean matches(HabboItem triggerItem, WiredEvent event) {
 *         return event.getText()
 *             .map(text -> text.toLowerCase().contains(keyword.toLowerCase()))
 *             .orElse(false);
 *     }
 * }
 * }</pre>
 * 
 * @see WiredEvent
 * @see IWiredCondition
 * @see IWiredEffect
 */
public interface IWiredTrigger {

    /**
     * Get the event type this trigger listens to.
     * The engine uses this for fast filtering before calling {@link #matches}.
     * 
     * @return the event type this trigger responds to
     */
    WiredEvent.Type listensTo();

    /**
     * Determine if this trigger matches the given event.
     * This is called after event type filtering to check trigger-specific conditions.
     * 
     * @param triggerItem the wired trigger furniture item
     * @param event the event that occurred
     * @return true if this trigger should activate for this event
     */
    boolean matches(HabboItem triggerItem, WiredEvent event);
    
    /**
     * Check if this trigger requires an actor (RoomUnit) to fire.
     * If true and no actor is present in the event, the trigger won't match.
     * Default is false for backwards compatibility.
     * 
     * @return true if an actor is required
     */
    default boolean requiresActor() {
        return false;
    }
}
