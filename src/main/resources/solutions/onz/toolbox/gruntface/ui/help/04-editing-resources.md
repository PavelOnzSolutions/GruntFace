# Editing resources

Click any node to open the inspector on the right.

For a **unit**, the inspector shows the unit's inputs alongside the variable
schema declared by its source module. Each input gets a typed editor:

- Strings, numbers, booleans — plain fields.
- Lists, sets, tuples, maps, objects — structured editors.
- Expressions GruntFace cannot evaluate are shown read-only and tagged as
  expressions.

For a **unit using an external module**, the inspector falls back to a
free-text editor for the `inputs` block — there is no local schema to drive
the form.

To save, the inspector opens a **diff preview** showing the exact change to
the file. If you cancel, nothing is written. If you confirm, GruntFace writes
the file atomically.

If the file changed on disk after GruntFace read it, a warning appears and the
save is blocked — reload before trying again.

To edit the raw HCL of a unit or include, right-click the node and choose
**Edit raw HCL…**. The full file opens in a syntax-highlighted editor and the
diff preview runs the same drift check.

To delete a unit or include, right-click the node and choose **Delete…**.
Deletion is confirmed and is not undoable from inside GruntFace.
