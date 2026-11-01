/** Resolve grouped mappings and standalone overrides without dropping source IDs. */
export function resolveRequirementMappings(rows, configured) {
  const known = new Set(rows.map(row => row.id));
  const mappings = {};
  const validate = id => {
    if (!known.has(id)) throw new Error(`Mapping references an absent source ID: ${id}`);
  };
  for (const group of configured.groups ?? []) {
    for (const id of group.ids) {
      validate(id);
      mappings[id] = { ...group.mapping, related: group.ids.filter(other => other !== id) };
    }
  }
  for (const [id, override] of Object.entries(configured.overrides ?? {})) {
    validate(id);
    mappings[id] = { related: [], ...mappings[id], ...override };
  }
  return mappings;
}
