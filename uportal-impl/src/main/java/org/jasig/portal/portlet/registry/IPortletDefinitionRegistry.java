/**
 * Copyright 2007 The JA-SIG Collaborative.  All rights reserved.
 * See license distributed with this file and
 * available online at http://www.uportal.org/license.html
 */
package org.jasig.portal.portlet.registry;

import org.jasig.portal.portlet.om.IPortletDefinition;
import org.jasig.portal.portlet.om.IPortletDefinitionId;

/**
 * Provides methods for creating and accessing {@link IPortletDefinition} and related objects.
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
public interface IPortletDefinitionRegistry {
    /**
     * Get an existing portlet definition for the definition id. If no definition exists for the id null will be returned.
     * 
     * @param portletDefinitionId The id of the definition to retrieve
     * @return The portlet definition for the id, null if no definition exists for the id.
     * @throws IllegalArgumentException If portletDefinitionId is null.
     */
    public IPortletDefinition getPortletDefinition(IPortletDefinitionId portletDefinitionId);
    
    /**
     * Get an existing portlet definition for the channel publish id. If no definition exists for the id null will be
     * returned.
     * 
     * @param channelPublishId The id of the {@link org.jasig.portal.ChannelDefinition} this portlet definition represents.
     * @return The portlet definition for the channelPublishId, null if no definition exists for the id.
     * @throws IllegalArgumentException If channelPublishId is null.
     */
    public IPortletDefinition getPortletDefinition(int channelPublishId);
    
    /**
     * Creates a new, persisted, portlet definition for the published channel, portlet application and portlet name. If
     * the * {@link org.jasig.portal.ChannelDefinition} for the channelPublishId can't be found or an definition already
     * exists for the channel definition id an exception will be thrown.
     * 
     * @param channelPublishId The id of the {@link org.jasig.portal.ChannelDefinition} this portlet definition represents.
     * @return A new definition for the parameters
     * @throws IllegalArgumentException If no ChannelDefinition exists for the id, or no portlet descriptors exists for ChannelDefinition
     */
    public IPortletDefinition createPortletDefinition(int channelPublishId);
    
    /**
     * Convience for {@link #getPortletDefinition(int)} and {@link #createPortletDefinition(int)}. If
     * the get returns null the definition will be created and returned.
     * 
     * @see #getPortletDefinition(int)
     * @see #createPortletDefinition(int)
     */
    public IPortletDefinition getOrCreatePortletDefinition(int channelPublishId);
}
