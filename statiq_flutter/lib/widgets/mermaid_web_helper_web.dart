import 'dart:convert';
import 'dart:html' as html;
import 'dart:ui_web' as ui_web;

class MermaidWebHelper {
  static void registerView(String viewId, String code) {
    ui_web.platformViewRegistry.registerViewFactory(viewId, (int id) {
      final String htmlContent = '''
      <!DOCTYPE html>
      <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          html, body { width: 100%; height: 100%; margin: 0; padding: 12px; overflow: auto; background-color: #131314; color: white; }
          .mermaid { width: 100%; min-height: 350px; }
        </style>
        <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
        <script>
          window.addEventListener('DOMContentLoaded', (event) => {
            mermaid.initialize({ startOnLoad: true, theme: 'dark', securityLevel: 'loose' });
          });
        </script>
      </head>
      <body>
        <div class="mermaid">
          $code
        </div>
      </body>
      </html>
      ''';

      final String base64Content = base64Encode(const Utf8Encoder().convert(htmlContent));
      
      return html.IFrameElement()
        ..style.width = '100%'
        ..style.height = '100%'
        ..style.border = 'none'
        ..src = 'data:text/html;charset=utf-8;base64,$base64Content';
    });
  }
}
