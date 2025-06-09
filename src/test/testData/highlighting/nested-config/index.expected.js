console.log('Happy developing ✨')

<warning descr="oxc: `debugger` statement is not allowed
help: Remove the debugger statement eslint(no-debugger)">debugger</warning>
<warning descr="oxc: Do not use `new Array(singleArgument)`.
help: It's not clear whether the argument is meant to be the length of the array or the only element. If the argument is the array's length, consider using `Array.from({ length: n })`. If the argument is the only element, use `[element]`. eslint-plugin-unicorn(no-new-array)">new Array([])</warning>

if (foo) <error descr="oxc: Expected { after 'if' condition.
help: Replace `foo++;` with `{foo++;}`. eslint(curly)">foo++;</error>
