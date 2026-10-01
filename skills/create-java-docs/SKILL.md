---
name: create-java-docs
description: Use when APIs have non-obvious contracts that callers need to understand. Use for methods or classes with edge cases, side effects, null handling, failure conditions, units, ranges, or lifecycle constraints. Skip obvious members and do not use for implementation, refactoring, or bug fixes.
---

# Create meaningful Javadoc

## Scope

Explain what callers need to use an API correctly. This skill edits documentation comments only; it does not change declarations, annotations, implementations, tests, or build configuration. In a broader coding task, apply it to the documentation portion after the relevant code exists. Separate any explicitly authorized implementation work from this skill.

## Workflow

1. Read repository instructions, nearby Javadoc, the target declaration and implementation, relevant callers/tests, and inherited contracts. Check the configured Java toolchain and documentation task before choosing syntax or validation commands.
2. Identify information the signature cannot convey: units, ranges, null/empty handling, ordering, mutation, resource ownership, failure conditions, or lifecycle. For classes, explain responsibility and usage constraints; for methods, explain observable outcomes. Skip straightforward members unless requested or required by project conventions.
3. Ground each claim in evidence. If existing documentation and implementation disagree, report the discrepancy; do not silently turn an apparent bug into a promised contract. Document unambiguous behavior and ask only when the unresolved contract prevents a correct edit. Do not invent thread safety, performance guarantees, release versions, or validation that the code does not perform.
4. Write a concise summary sentence, then only necessary contract details and tags. Prefer caller-facing semantics over a narration of implementation steps. Preserve useful existing documentation and the project's language and style.
5. Review the documentation-only diff and run the existing documentation check with the configured JDK when available. Inspect changed generated entries for rendering and link problems. Report edits, checks actually run, unresolved contract questions, and any tooling limitation.

## Syntax and decisions

- Default to Java 21-compatible `/** ... */` comments immediately before the declaration, above annotations. Do not introduce newer syntax merely because a newer JDK is installed.
- Put the summary and explanatory paragraphs before block tags. Start each block tag on its own line; continue its description on subsequent lines if needed. Use `<p>` for additional paragraphs, not Markdown formatting inside Java 21 comments.
- Use `@param` for meaningful input constraints, including `<T>` for type parameters and component names for records. Use `@return` for result semantics on non-void methods; omit it for constructors and void methods. Follow project completeness checks without filling tags with repeated names.
- Explain caller-relevant checked and unchecked failures with `@throws` and the triggering condition, rather than listing every exception reachable in internals.
- Use `{@code ...}` for code/literals, `{@link Type#method(Type)}` for resolvable API references, and `@see` for related reading. Keep HTML valid; raw angle brackets can be interpreted as markup.
- Inspect an overridden method's documentation first. Rely on inheritance when it already describes the contract; use `{@inheritDoc}` in the method's main description or the descriptions of its `@param`, `@return`, or `@throws` tags when adding relevant detail. Do not use it for classes or constructors.
- Treat tag order, `@author`, `@version`, and `@since` policies as local conventions, not universal requirements. Add release metadata only with evidence. Explain an existing deprecation and its replacement without adding or changing `@Deprecated` under this skill.
- Use plain prose for implementation notes unless the project configures custom tags such as `@implNote` or `@apiNote`. Do not change the build to make a comment pass.

## Common mistakes

- **Repeating the name:** “Gets the deadline.” Replace with a useful contract, such as “Returns the deadline in epoch milliseconds, or zero if no deadline is set,” only when verified.
- **Promising accidental behavior:** accepting a negative argument today does not establish intended support when the existing contract forbids it. Flag that conflict instead of broadening the contract.
- **Copying tutorial configuration:** retain the project's toolchain and documentation settings; an example's Java source level and plugin version are not requirements.
- **Treating successful generation as proof:** rendering checks catch malformed documentation, not false promises. Compare the text with code, tests, and inherited contracts as well. Report pre-existing failures separately; do not suppress checks or repair code to pass a documentation task.

## Example: useful contract versus repeated names

Illustrative method inside an existing class; no dependencies. Its implementation is shown only to make the documentation verifiable, not as code to add during documentation work.

Bad: valid syntax, but it omits units, rounding, and invalid inputs.

```java
/**
 * Converts a timeout.
 * @param millis the millis
 * @return the converted timeout
 */
```

Good: the same method's contract tells a caller how to use it correctly.

```java
/**
 * Converts a nonnegative timeout from milliseconds to whole seconds,
 * rounding up so that a positive timeout never becomes zero.
 *
 * @param millis the timeout in milliseconds; zero means no waiting
 * @return the timeout in seconds, rounded up; zero if {@code millis} is zero
 * @throws IllegalArgumentException if {@code millis} is negative
 */
public static long timeoutSeconds(long millis) {
    if (millis < 0) {
        throw new IllegalArgumentException("Negative timeout");
    }
    return millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
}
```

The example assumes the surrounding API defines zero as no waiting. The arithmetic alone does not establish that meaning: in another project, verify it from the caller or contract before using that wording.
