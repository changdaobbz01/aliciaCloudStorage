UPDATE storage_node
SET active_node_name = CASE WHEN is_deleted = 0 THEN LOWER(node_name) ELSE NULL END;
