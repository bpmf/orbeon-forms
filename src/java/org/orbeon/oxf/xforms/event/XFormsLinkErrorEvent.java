/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event;

import org.dom4j.Element;

/**
 * 4.5.3 The xforms-link-error Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: The URI that failed to load (xsd:anyURI)
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsLinkErrorEvent extends org.orbeon.oxf.xforms.event.XFormsEvent {
    private Throwable throwable;
    private Element controlElement;
    private String urlString;

    public XFormsLinkErrorEvent(Object targetObject, String urlString, Element controlElement, Throwable throwable) {
        super(org.orbeon.oxf.xforms.event.XFormsEvents.XFORMS_LINK_ERROR, targetObject, true, false);
        this.urlString = urlString;
        this.controlElement = controlElement;
        this.throwable = throwable;
    }

    public Element getControlElement() {
        return controlElement;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String getUrlString() {
        return urlString;
    }
}
