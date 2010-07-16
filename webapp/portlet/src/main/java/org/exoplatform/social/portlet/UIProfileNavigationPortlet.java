/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.portlet;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.model.ProfileAttachment;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.webui.URLUtils;
import org.exoplatform.social.webui.Utils;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.web.application.RequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.lifecycle.UIApplicationLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

/**
 * Manages the navigation of connections.<br>
 *   - Decides which node is current selected.<br>
 *   - Checked is view by current user or by another.<br>
 */

@ComponentConfig(
 lifecycle = UIApplicationLifecycle.class,
 template = "app:/groovy/social/portlet/UIProfileNavigationPortlet.gtmpl",
 events = {
   @EventConfig(listeners = UIProfileNavigationPortlet.AddContactActionListener.class),
   @EventConfig(listeners = UIProfileNavigationPortlet.AcceptContactActionListener.class),
   @EventConfig(listeners = UIProfileNavigationPortlet.DenyContactActionListener.class)
 }
)
public class UIProfileNavigationPortlet extends UIPortletApplication {
  /** Label for display invoke action */
  private static final String INVITATION_REVOKED_INFO = "UIProfileNavigationPortlet.label.RevokedInfo";
  
  /** Label for display established invitation */
  private static final String INVITATION_ESTABLISHED_INFO = "UIProfileNavigationPortlet.label.InvitationEstablishedInfo";
  
  private IdentityManager identityManager;
  private RelationshipManager relationshipManager;
  /**
   * Default Constructor.<br>
   * 
   * @throws Exception
   */
  public UIProfileNavigationPortlet() throws Exception { }
  
  /**
   * Returns the current selected node.<br>
   * 
   * @return selected node.
   */
  public String getSelectedNode() {
    PortalRequestContext pcontext = Util.getPortalRequestContext();
    String requestUrl = pcontext.getRequestURI();
    String[] split = requestUrl.split("/");
    if (split.length == 6) {
      return split[split.length - 2];
    } else if (split.length == 7) {
      return split[split.length - 3];
    }
    return split[split.length-1];
  }
  
  /**
   * Checked is view by current user or by another.<br>
   * 
   * @return true if it is viewed by current login user.
   */
  public boolean isMe () {
    RequestContext context = RequestContext.getCurrentInstance();
    String currentUserName = context.getRemoteUser();
    String currentViewer = URLUtils.getCurrentUser();
    
    if (currentViewer == null) {
      return true;
    }
    
    return currentUserName.equals(currentViewer);
  }
  
  /**
   * Gets contact status between current user and identity that is checked.<br>
   * 
   * @param identity
   *        Object is checked status with current user.
   *        
   * @return type of relationship status that equivalent the relationship.
   * 
   * @throws Exception
   */
  public Relationship.Type getContactStatus() throws Exception {
    Identity viewerIdentity = getCurrentViewerIdentity();
    if (viewerIdentity.getId().equals(getCurrentIdentity().getId()))
      return Relationship.Type.SELF;
    RelationshipManager rm = getRelationshipManager();
    Relationship rl = rm.getRelationship(viewerIdentity, getCurrentIdentity());
    return rm.getRelationshipStatus(rl, getCurrentIdentity());
  }
  
  /**
   * Listens to add action then make request to invite person to make connection.<br>
   *   - Gets information of user is invited.<br>
   *   - Checks the relationship to confirm that there have not got connection yet.<br>
   *   - Saves the new connection.<br>
   *
   */
  public static class AddContactActionListener extends EventListener<UIProfileNavigationPortlet> {
    public void execute(Event<UIProfileNavigationPortlet> event) throws Exception {
      UIProfileNavigationPortlet profileNavigationportlet = event.getSource();
      Identity currIdentity = profileNavigationportlet.getCurrentIdentity();
      Identity viewerIdentity = profileNavigationportlet.getCurrentViewerIdentity();
      RelationshipManager rm = profileNavigationportlet.getRelationshipManager();
      Relationship rel = rm.getRelationship(currIdentity, viewerIdentity);
      // Check if invitation is established by another user
      UIApplication uiApplication = event.getRequestContext().getUIApplication();
      Relationship.Type relationStatus = profileNavigationportlet.getContactStatus();
      if (relationStatus != Relationship.Type.ALIEN) {
        uiApplication.addMessage(new ApplicationMessage(INVITATION_ESTABLISHED_INFO, null, ApplicationMessage.INFO));
        return;
      }
      if (rel == null) {
        rel = rm.invite(currIdentity, viewerIdentity);
      } else {
        rm.confirm(rel);
      }
    }
  }

  /**
   * Listens to accept actions then make connection to accepted person.<br>
   *   - Gets information of user who made request.<br>
   *   - Checks the relationship to confirm that there still got invited connection.<br>
   *   - Makes and Save the new relationship.<br>
   */
  public static class AcceptContactActionListener extends EventListener<UIProfileNavigationPortlet> {
    public void execute(Event<UIProfileNavigationPortlet> event) throws Exception {
      UIProfileNavigationPortlet profileNavigationportlet = event.getSource();
      UIApplication uiApplication = event.getRequestContext().getUIApplication();
      Identity currIdentity = profileNavigationportlet.getCurrentIdentity();
      Identity viewerIdentity = profileNavigationportlet.getCurrentViewerIdentity();
      RelationshipManager rm = profileNavigationportlet.getRelationshipManager();
      // Check if invitation is revoked or deleted by another user
      Relationship rel = rm.getRelationship(currIdentity, viewerIdentity);
      Relationship.Type relationStatus = profileNavigationportlet.getContactStatus();
      if (relationStatus == Relationship.Type.ALIEN) {
        uiApplication.addMessage(new ApplicationMessage(INVITATION_REVOKED_INFO, null, ApplicationMessage.INFO));
        return;
      }
      rm.confirm(rel);
    }
  }

  /**
   * Listens to deny action then delete the invitation.<br>
   *   - Gets information of user is invited or made request.<br>
   *   - Checks the relation to confirm that there have not got relation yet.<br>
   *   - Removes the current relation and save the new relation.<br> 
   *
   */
  public static class DenyContactActionListener extends EventListener<UIProfileNavigationPortlet> {
    public void execute(Event<UIProfileNavigationPortlet> event) throws Exception {
      UIProfileNavigationPortlet profileNavigationportlet = event.getSource();
      Identity currIdentity = profileNavigationportlet.getCurrentIdentity();
      Identity viewerIdentity = profileNavigationportlet.getCurrentViewerIdentity();
      RelationshipManager rm = profileNavigationportlet.getRelationshipManager();
      // Check if invitation is revoked or deleted by another user
      UIApplication uiApplication = event.getRequestContext().getUIApplication();
      Relationship.Type relationStatus = profileNavigationportlet.getContactStatus();
      if (relationStatus == Relationship.Type.ALIEN) {
        uiApplication.addMessage(new ApplicationMessage(INVITATION_REVOKED_INFO, null, ApplicationMessage.INFO));
        return;
      }
      Relationship rel = rm.getRelationship(currIdentity, viewerIdentity);
      if (rel != null) {
        rm.remove(rel);
      }
    }
  }
  
  /**
   * Gets information about source that avatar image is stored.<br>
   *  
   * @return image source address.
   * 
   * @throws Exception
   */
  protected String getImageSource() throws Exception {
    Identity currIdentity = Utils.getCurrentIdentity();
    Profile p = currIdentity.getProfile();
    ProfileAttachment att = (ProfileAttachment) p.getProperty(Profile.AVATAR);
    if (att != null) {
      return "/"+ getRestContext() + "/jcr/" + getRepository()+ "/" + att.getWorkspace()
              + att.getDataPath() + "/?rnd=" + System.currentTimeMillis();
    }
    return null;
  }
  
  /**
   * Gets the current repository.<br>
   * 
   * @return current repository through repository service.
   * 
   * @throws Exception
   */
  private String getRepository() throws Exception {
    RepositoryService rService = getApplicationComponent(RepositoryService.class);
    return rService.getCurrentRepository().getConfiguration().getName();
  }
  
  /**
   * Gets the rest context.
   * 
   * @return the rest context
   */
  private String getRestContext() {
    return PortalContainer.getInstance().getRestContextName();
  }
  
  /**
   * Gets current identity.<br>
   * 
   * @return identity of current login user.
   * 
   * @throws Exception
   */
  public Identity getCurrentIdentity() throws Exception {
      IdentityManager im = getIdentityManager();
      return im.getOrCreateIdentity(OrganizationIdentityProvider.NAME, getCurrentUserName());
  }
  
  /**
   * Gets the identity of current user is viewed by another.<br>
   * 
   * @return identity of current user who is viewed.
   * 
   * @throws Exception
   */
  public Identity getCurrentViewerIdentity() throws Exception {
    IdentityManager im = getIdentityManager();
    return im.getOrCreateIdentity(OrganizationIdentityProvider.NAME, getCurrentViewerUserName());
  }
  
  /**
   * Gets currents name of user that is viewed by another.<br>
   *
   * @return name of user who is viewed.
   */
  private String getCurrentViewerUserName() {
    String username = URLUtils.getCurrentUser();
    if(username != null)
      return username;

    PortalRequestContext portalRequest = Util.getPortalRequestContext();

    return portalRequest.getRemoteUser();
  }
  
  /**
   * Gets identity manager object.<br>
   * 
   * @return identity manager object.
   */
  private IdentityManager getIdentityManager() {
    if (identityManager == null) {
      PortalContainer container = PortalContainer.getInstance();
      identityManager = (IdentityManager) container.getComponentInstanceOfType(IdentityManager.class);
    }
    return identityManager;
  }

  /**
   * Gets relationshipManager object.<br>
   * 
   * @return
   */
  private RelationshipManager getRelationshipManager() {
    if (relationshipManager == null) {
      PortalContainer container = PortalContainer.getInstance();
      relationshipManager = (RelationshipManager) container
        .getComponentInstanceOfType(RelationshipManager.class);
    }
    
    return relationshipManager;
  }
  
  /**
   * Gets name of current user.
   * 
   * @return name of current login user.
   */
  private String getCurrentUserName() {
    RequestContext context = RequestContext.getCurrentInstance();
    return context.getRemoteUser();
  }
}

