/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.eval;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.redhat.lightblue.crud.ArrayRemoveByQueryExpression;

import com.redhat.lightblue.metadata.EntityMetadata;
import com.redhat.lightblue.metadata.FieldTreeNode;
import com.redhat.lightblue.metadata.ArrayField;

import com.redhat.lightblue.util.Path;
import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.KeyValueCursor;
import com.redhat.lightblue.util.MutablePath;

/**
 * Removes elements that match a query from an array
 */
public class ArrayRemoveByQueryEvaluator extends Updater {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArrayRemoveByQueryEvaluator.class);

    private final Path field;
    private final QueryEvaluator query;

    public ArrayRemoveByQueryEvaluator(EntityMetadata md, ArrayRemoveByQueryExpression expr) {
        this.field = expr.getField();
        FieldTreeNode node = md.resolve(field);
        if (node instanceof ArrayField) {
            this.query = QueryEvaluator.getInstance(expr.getQuery(), ((ArrayField) node).getElement());
        } else {
            throw new EvaluationError("Expected array field:" + field);
        }
    }

    /**
     * Removes the elements that match the query
     */
    @Override
    public boolean update(JsonDoc doc) {
        boolean ret = false;
        LOGGER.debug("Remove elements from {} ", field);
        KeyValueCursor<Path, JsonNode> cursor = doc.getAllNodes(field);
        while (cursor.hasNext()) {
            JsonNode node = cursor.getCurrentValue();
            if (node instanceof ArrayNode) {
                MutablePath path = new MutablePath(cursor.getCurrentKey());
                int index = 0;
                path.push(index);
                List<Integer> deleteList = new ArrayList<Integer>();
                for (Iterator<JsonNode> itr = ((ArrayNode) node).elements(); itr.hasNext();) {
                    JsonNode element = itr.next();
                    QueryEvaluationContext ctx = new QueryEvaluationContext(element, path.immutableCopy());
                    if (query.evaluate(ctx)) {
                        deleteList.add(index);
                    }
                    index++;
                    path.setLast(index);
                }
                LOGGER.debug("Removing {} from {}", deleteList, field);
                for (int i = deleteList.size() - 1; i >= 0; i--) {
                    ((ArrayNode) node).remove(deleteList.get(i));
                }
            } else {
                LOGGER.warn("Expected array node for {}, got {}", cursor.getCurrentKey(), node.getClass().getName());
            }
        }
        return ret;
    }
}
