import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; // For rootBundle
import 'package:webview_flutter/webview_flutter.dart';
import 'mermaid_web_helper.dart' as web_helper;

class MermaidRenderWidget extends StatefulWidget {
  final String code;

  const MermaidRenderWidget({super.key, required this.code});

  @override
  State<MermaidRenderWidget> createState() => _MermaidRenderWidgetState();
}

class _MermaidRenderWidgetState extends State<MermaidRenderWidget> {
  // Использовать late только для мобильных платформ во избежание краша на Web
  late final WebViewController _controller;
  bool _isLoading = true;
  String? _mermaidJsCode;
  late String _viewId;

  @override
  void initState() {
    super.initState();
    _viewId = 'mermaid-view-${identityHashCode(this)}';

    if (!kIsWeb) {
      // Инициализируем контроллер только для Android/iOS
      _controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.transparent)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageFinished: (String url) {
              if (mounted) {
                setState(() {
                  _isLoading = false;
                });
              }
            },
          ),
        );
      _initAndLoadMobile();
    } else {
      _isLoading = false; // На вебе инициализация фабрики происходит синхронно
    }
  }

  String _buildHtml() {
    return '''
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
      <style>
        body {
          margin: 0;
          padding: 16px;
          background-color: transparent;
          display: flex;
          justify-content: center;
          align-items: center;
        }
        #mermaid-container {
          width: 100%;
          min-width: 600px; /* Предотвращаем сжатие схемы */
        }
      </style>
      <script>
        $_mermaidJsCode
      </script>
      <script>
        mermaid.initialize({
          startOnLoad: true,
          theme: 'dark',
          securityLevel: 'loose'
        });
      </script>
    </head>
    <body>
      <div class="mermaid" id="mermaid-container">
        ${widget.code}
      </div>
    </body>
    </html>
    ''';
  }

  Future<void> _initAndLoadMobile() async {
    try {
      _mermaidJsCode ??= await rootBundle.loadString('assets/js/mermaid.min.js');
      if (!mounted) return;

      final String htmlContent = _buildHtml();
      final String contentBase64 = base64Encode(const Utf8Encoder().convert(htmlContent));
      await _controller.loadRequest(Uri.parse('data:text/html;base64,$contentBase64'));
    } catch (e) {
      debugPrint('Error loading mobile Mermaid widget: $e');
    }
  }

  @override
  void didUpdateWidget(covariant MermaidRenderWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.code != widget.code) {
      if (kIsWeb) {
        // Генерируем уникальный ID для обновления отображения фрейма
        setState(() {
          _viewId = 'mermaid-view-${identityHashCode(this)}-${DateTime.now().millisecondsSinceEpoch}';
        });
      } else {
        if (mounted) {
          setState(() {
            _isLoading = true;
          });
        }
        _initAndLoadMobile();
      }
    }
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;

    if (kIsWeb) {
      // Регистрируем фабрику синхронно прямо во время build
      web_helper.MermaidWebHelper.registerView(_viewId, widget.code);
    }
    
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 12),
      height: size.height * 0.4, // Ограничиваем высоту графика 40% от высоты экрана
      width: double.infinity,
      decoration: BoxDecoration(
        color: Theme.of(context).brightness == Brightness.dark 
            ? const Color(0xFF131314) 
            : const Color(0xFFF1F5F9),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: Theme.of(context).brightness == Brightness.dark
              ? Colors.white.withValues(alpha: 0.08)
              : const Color(0xFFE2E8F0),
        ),
      ),
      child: Stack(
        children: [
          kIsWeb
              ? HtmlElementView(viewType: _viewId)
              : InteractiveViewer(
                  boundaryMargin: const EdgeInsets.all(40),
                  minScale: 0.5,
                  maxScale: 4.0,
                  child: WebViewWidget(controller: _controller),
                ),
          if (_isLoading)
            const Center(
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
        ],
      ),
    );
  }
}
