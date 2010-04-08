/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.Expression;

import java.util.Map;

public class ControlAnalysis {

    public final XFormsStaticState staticState;

    public final XBLBindings.Scope scope;
    public final String prefixedId;
    public final Element element;
    public final LocationData locationData;
    public final int index;
    public final boolean hasNodeBinding;
    public final boolean isValueControl;

    public final ContainerAnalysis parentControlAnalysis;
    public final Map<String, ControlAnalysis> inScopeVariables; // variable name -> ControlAnalysis
    
    private final Element nestedLabel;
    private final Element nestedHelp;
    private final Element nestedHint;
    private final Element nestedAlert;

    private Element externalLabel;
    private Element externalHelp;
    private Element externalHint;
    private Element externalAlert;

    public final String modelPrefixedId;

    public final XPathAnalysis bindingAnalysis;
    public final XPathAnalysis valueAnalysis;

    private String classes;

    public ControlAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo,
                           XBLBindings.Scope scope, String prefixedId, Element element, LocationData locationData, int index,
                           boolean hasNodeBinding, boolean isValueControl, ContainerAnalysis parentControlAnalysis,
                           Map<String, ControlAnalysis> inScopeVariables) {

        this.staticState = staticState;
        this.scope = scope;
        this.prefixedId = prefixedId;
        this.element = element;
        this.locationData = locationData;
        this.index = index;
        this.hasNodeBinding = hasNodeBinding;
        this.isValueControl = isValueControl;
        this.parentControlAnalysis = parentControlAnalysis;
        this.inScopeVariables = inScopeVariables;

        this.nestedLabel = findNestedLHHAElement(XFormsConstants.LABEL_QNAME);
        this.nestedHelp = findNestedLHHAElement(XFormsConstants.HELP_QNAME);
        this.nestedHint = findNestedLHHAElement(XFormsConstants.HINT_QNAME);
        this.nestedAlert = findNestedLHHAElement(XFormsConstants.ALERT_QNAME);

        this.modelPrefixedId = computeModelPrefixedId();

        if (staticState.isXPathAnalysis()) {
            this.bindingAnalysis = computeBindingAnalysis();
            this.valueAnalysis = computeValueAnalysis();
        } else {
            this.bindingAnalysis = null;
            this.valueAnalysis = null;
        }
    }

    protected Element findNestedLHHAElement(QName qName) {
        return element.element(qName);
    }

    public void setExternalLHHA(Element lhhaElement) {
        final String name = lhhaElement.getName();
        if (XFormsConstants.LABEL_QNAME.getName().equals(name)) {
            externalLabel = lhhaElement;
        } else if (XFormsConstants.HELP_QNAME.getName().equals(name)) {
            externalHelp = lhhaElement;
        } else if (XFormsConstants.HINT_QNAME.getName().equals(name)) {
            externalHint = lhhaElement;
        } else if (XFormsConstants.ALERT_QNAME.getName().equals(name)) {
            externalAlert = lhhaElement;
        }
    }

    public Element getLabelElement() {
        return (nestedLabel != null) ? nestedLabel : externalLabel;
    }

    public Element getHelpElement() {
        return (nestedHelp != null) ? nestedHelp : externalHelp;
    }

    public Element getHintElement() {
        return (nestedHint != null) ? nestedHint : externalHint;
    }

    public Element getAlertElement() {
        return (nestedAlert != null) ? nestedAlert : externalAlert;
    }

    protected String computeModelPrefixedId() {
        if (element != null) {
            final String localModelId = element.attributeValue("model");
            if (localModelId != null) {
                // Get model prefixed id and verify it belongs to this scope
                final String localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelId);
                if (staticState.getModel(localModelPrefixedId) == null)
                    throw new ValidationException("Reference to non-existing model id: " + localModelId, locationData);
                return localModelPrefixedId;
            } else {
                final ControlAnalysis ancestor = getAncestorControlAnalysisInScope();
                if (ancestor != null) {
                    // There is an ancestor control in the same scope, use its model id
                    return ancestor.modelPrefixedId;
                } else {
                    // Top-level control in a new scope, use default model id for scope
                    return staticState.getDefaultModelPrefixedIdForScope(scope);
                }
            }
        } else {
            return null;
        }
    }

    protected XPathAnalysis computeBindingAnalysis() {
        if (element != null) {
            final String bindingExpression;
            if (element.attributeValue("context") == null) {
                final String ref = element.attributeValue("ref");
                if (ref != null) {
                    bindingExpression = ref;
                } else {
                    bindingExpression = element.attributeValue("nodeset");
                }
            } else {
                // TODO: handle @context
                bindingExpression = null;
            }

            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis();
            if ((bindingExpression != null)) {
                // New binding expression
                return analyzeXPath(staticState, baseAnalysis, prefixedId, bindingExpression);
            } else {
                // TODO: TEMP: just do this for now so that controls w/o their own binding also get binding updated
                return baseAnalysis;
            }
        } else {
            return null;
        }
    }

    protected XPathAnalysis findOrCreateBaseAnalysis() {
        final XPathAnalysis baseAnalysis;
        final XPathAnalysis ancestor = getAncestorOrSelfBindingAnalysis();
        if (ancestor != null) {
            // There is an ancestor control in the same scope with same model, use its analysis as base
            baseAnalysis = ancestor;
        } else {
            // We are a top-level control in a scope/model combination, create analysis
            if (modelPrefixedId != null) {
                final String defaultInstancePrefixedId = staticState.getDefaultInstancePrefixedIdForScope(scope);
                if (defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    baseAnalysis = analyzeXPath(staticState, null, prefixedId, XPathAnalysis.buildInstanceString(defaultInstancePrefixedId));
                } else {
                    // No default instance
                    baseAnalysis = null;
                }
            } else {
                // No default model
                baseAnalysis = null;
            }
        }
        return baseAnalysis;
    }

    protected XPathAnalysis computeValueAnalysis () {
        if (element != null) {
            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis();
            if (isValueControl) {
                final String valueAttribute = element.attributeValue("value");

                final boolean isXXFormsAttribute = element.getQName().equals(XFormsConstants.XXFORMS_ATTRIBUTE_QNAME);
                if (isXXFormsAttribute) {
                    // TODO
                    // NOTE: bad design that AVT has @value as attribute name
                    return null;
                } else if (valueAttribute != null) {
                    // E.g. xforms:output/@value
                    return analyzeXPath(staticState, baseAnalysis, prefixedId, valueAttribute);
                } else {
                    // Value is considered the string value
                    return analyzeXPath(staticState, baseAnalysis, prefixedId, "string()");
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private ControlAnalysis getAncestorControlAnalysisInScope() {
        ControlAnalysis currentControlAnalysis = parentControlAnalysis;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentControlAnalysis;
        }

        return null;
    }

    private XPathAnalysis getAncestorOrSelfBindingAnalysis() {
        ControlAnalysis currentControlAnalysis = this;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.bindingAnalysis != null
                    && currentControlAnalysis.modelPrefixedId.equals(modelPrefixedId)
                    && currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis.bindingAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentControlAnalysis;
        }

        return null;
    }

    protected XPathAnalysis analyzeXPath(XFormsStaticState staticState, XPathAnalysis baseAnalysis, String prefixedId, String xpathString) {
        // Create new expression
        // TODO: get expression from pool and pass in-scope variables (probably more efficient)
        final Expression expression = XPathCache.createExpression(staticState.getXPathConfiguration(), xpathString,
                staticState.getMetadata().namespaceMappings.get(prefixedId), XFormsContainingDocument.getFunctionLibrary());
        // Analyse it
        return new XPathAnalysis(staticState, expression, xpathString, baseAnalysis, inScopeVariables, scope, modelPrefixedId);

    }

    public void addClasses(String classes) {
        if (this.classes == null) {
            // Set
            this.classes = classes;
        } else {
            // Append
            this.classes = this.classes + ' ' + classes;
        }
    }

    public String getClasses() {
        return classes;
    }

    public int getLevel() {
        if (parentControlAnalysis == null)
            return 0;
        else
            return parentControlAnalysis.getLevel() + 1;
    }

    public RepeatAnalysis getAncestorRepeat() {
        ContainerAnalysis currentParent = parentControlAnalysis;
        while (currentParent != null) {
            if (currentParent instanceof RepeatAnalysis)
                return (RepeatAnalysis) currentParent;
            currentParent = currentParent.parentControlAnalysis;
        }
        return null;
    }
}