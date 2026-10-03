# Noct for Visual Studio Code

Syntax highlighting for `.noct` files: keywords, strings with `{name}`
interpolation and escapes, numbers, operators and comments. Pressing Enter
after a line ending in `:` indents the next line automatically.

## Install

The extension is not on the Marketplace. To install it locally, copy this
folder into your VS Code extensions directory and restart VS Code:

```sh
# macOS / Linux
cp -r editors/vscode ~/.vscode/extensions/noct

# Windows (PowerShell)
Copy-Item -Recurse editors\vscode "$env:USERPROFILE\.vscode\extensions\noct"
```

Alternatively, package it as a `.vsix` with
[vsce](https://github.com/microsoft/vscode-vsce) and install that:

```sh
cd editors/vscode
npx @vscode/vsce package
code --install-extension noct-1.0.0.vsix
```
