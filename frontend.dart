// File: main.dart
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

void main() => runApp(BillingApp());

class BillingApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Billing Software',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _currentIndex = 0;

  final tabs = [
    CustomerPage(),
    ProductPage(),
    InvoicePage(),
    LedgerPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Billing Software')),
      body: tabs[_currentIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        items: [
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Customers'),
          BottomNavigationBarItem(icon: Icon(Icons.shopping_cart), label: 'Products'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt), label: 'Invoices'),
          BottomNavigationBarItem(icon: Icon(Icons.account_balance), label: 'Ledgers'),
        ],
        onTap: (index) => setState(() => _currentIndex = index),
      ),
    );
  }
}

/* -------------------- CUSTOMER PAGE -------------------- */
class CustomerPage extends StatefulWidget {
  @override
  _CustomerPageState createState() => _CustomerPageState();
}

class _CustomerPageState extends State<CustomerPage> {
  List customers = [];

  @override
  void initState() {
    super.initState();
    fetchCustomers();
  }

  fetchCustomers() async {
    final response = await http.get(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/customers'));
    if (response.statusCode == 200) {
      setState(() => customers = json.decode(response.body));
    }
  }

  addCustomer(String name, String email, String phone) async {
    final response = await http.post(
      Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/customers'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'name': name, 'email': email, 'phone': phone}),
    );
    if (response.statusCode == 200 || response.statusCode == 201) fetchCustomers();
  }

  deleteCustomer(int id) async {
    await http.delete(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/customers/$id'));
    fetchCustomers();
  }

  @override
  Widget build(BuildContext context) {
    TextEditingController nameCtrl = TextEditingController();
    TextEditingController emailCtrl = TextEditingController();
    TextEditingController phoneCtrl = TextEditingController();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              TextField(controller: nameCtrl, decoration: InputDecoration(hintText: 'Name')),
              TextField(controller: emailCtrl, decoration: InputDecoration(hintText: 'Email')),
              TextField(controller: phoneCtrl, decoration: InputDecoration(hintText: 'Phone')),
              ElevatedButton(
                onPressed: () {
                  addCustomer(nameCtrl.text, emailCtrl.text, phoneCtrl.text);
                  nameCtrl.clear(); emailCtrl.clear(); phoneCtrl.clear();
                },
                child: Text('Add Customer'),
              )
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: customers.length,
            itemBuilder: (context, index) {
              var c = customers[index];
              return ListTile(
                title: Text(c['name']),
                subtitle: Text('${c['email']} | ${c['phone']}'),
                trailing: IconButton(icon: Icon(Icons.delete), onPressed: () => deleteCustomer(c['id'])),
              );
            },
          ),
        )
      ],
    );
  }
}

/* -------------------- PRODUCT PAGE -------------------- */
class ProductPage extends StatefulWidget {
  @override
  _ProductPageState createState() => _ProductPageState();
}

class _ProductPageState extends State<ProductPage> {
  List products = [];

  @override
  void initState() {
    super.initState();
    fetchProducts();
  }

  fetchProducts() async {
    final response = await http.get(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/products'));
    if (response.statusCode == 200) setState(() => products = json.decode(response.body));
  }

  addProduct(String name, double price, int stock) async {
    final response = await http.post(
      Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/products'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'name': name, 'price': price, 'stock': stock}),
    );
    if (response.statusCode == 200 || response.statusCode == 201) fetchProducts();
  }

  deleteProduct(int id) async {
    await http.delete(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/products/$id'));
    fetchProducts();
  }

  @override
  Widget build(BuildContext context) {
    TextEditingController nameCtrl = TextEditingController();
    TextEditingController priceCtrl = TextEditingController();
    TextEditingController stockCtrl = TextEditingController();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              TextField(controller: nameCtrl, decoration: InputDecoration(hintText: 'Product Name')),
              TextField(controller: priceCtrl, decoration: InputDecoration(hintText: 'Price'), keyboardType: TextInputType.number),
              TextField(controller: stockCtrl, decoration: InputDecoration(hintText: 'Stock'), keyboardType: TextInputType.number),
              ElevatedButton(
                onPressed: () {
                  addProduct(nameCtrl.text, double.parse(priceCtrl.text), int.parse(stockCtrl.text));
                  nameCtrl.clear(); priceCtrl.clear(); stockCtrl.clear();
                },
                child: Text('Add Product'),
              )
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: products.length,
            itemBuilder: (context, index) {
              var p = products[index];
              return ListTile(
                title: Text(p['name']),
                subtitle: Text('Price: ${p['price']} | Stock: ${p['stock']}'),
                trailing: IconButton(icon: Icon(Icons.delete), onPressed: () => deleteProduct(p['id'])),
              );
            },
          ),
        )
      ],
    );
  }
}

/* -------------------- INVOICE PAGE -------------------- */
class InvoicePage extends StatefulWidget {
  @override
  _InvoicePageState createState() => _InvoicePageState();
}

class _InvoicePageState extends State<InvoicePage> {
  List invoices = [];

  @override
  void initState() {
    super.initState();
    fetchInvoices();
  }

  fetchInvoices() async {
    final response = await http.get(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/invoices'));
    if (response.statusCode == 200) setState(() => invoices = json.decode(response.body));
  }

  addInvoice(int customerId, double totalAmount) async {
    final response = await http.post(
      Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/invoices'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'customerId': customerId, 'totalAmount': totalAmount}),
    );
    if (response.statusCode == 200 || response.statusCode == 201) fetchInvoices();
  }

  deleteInvoice(int id) async {
    await http.delete(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/invoices/$id'));
    fetchInvoices();
  }

  @override
  Widget build(BuildContext context) {
    TextEditingController customerCtrl = TextEditingController();
    TextEditingController amountCtrl = TextEditingController();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              TextField(controller: customerCtrl, decoration: InputDecoration(hintText: 'Customer ID'), keyboardType: TextInputType.number),
              TextField(controller: amountCtrl, decoration: InputDecoration(hintText: 'Total Amount'), keyboardType: TextInputType.number),
              ElevatedButton(
                onPressed: () {
                  addInvoice(int.parse(customerCtrl.text), double.parse(amountCtrl.text));
                  customerCtrl.clear(); amountCtrl.clear();
                },
                child: Text('Add Invoice'),
              )
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: invoices.length,
            itemBuilder: (context, index) {
              var i = invoices[index];
              return ListTile(
                title: Text('Invoice ID: ${i['id']}'),
                subtitle: Text('Customer: ${i['customerId']} | Total: ${i['totalAmount']}'),
                trailing: IconButton(icon: Icon(Icons.delete), onPressed: () => deleteInvoice(i['id'])),
              );
            },
          ),
        )
      ],
    );
  }
}

/* -------------------- LEDGER PAGE -------------------- */
class LedgerPage extends StatefulWidget {
  @override
  _LedgerPageState createState() => _LedgerPageState();
}

class _LedgerPageState extends State<LedgerPage> {
  List ledgers = [];

  @override
  void initState() {
    super.initState();
    fetchLedgers();
  }

  fetchLedgers() async {
    final response = await http.get(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/ledgers'));
    if (response.statusCode == 200) setState(() => ledgers = json.decode(response.body));
  }

  addLedger(String account, double balance) async {
    final response = await http.post(
      Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/ledgers'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'account': account, 'balance': balance}),
    );
    if (response.statusCode == 200 || response.statusCode == 201) fetchLedgers();
  }

  deleteLedger(int id) async {
    await http.delete(Uri.parse('http://<YOUR_BACKEND_IP>:8080/api/ledgers/$id'));
    fetchLedgers();
  }

  @override
  Widget build(BuildContext context) {
    TextEditingController accountCtrl = TextEditingController();
    TextEditingController balanceCtrl = TextEditingController();

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
            children: [
              TextField(controller: accountCtrl, decoration: InputDecoration(hintText: 'Account Name')),
              TextField(controller: balanceCtrl, decoration: InputDecoration(hintText: 'Balance'), keyboardType: TextInputType.number),
              ElevatedButton(
                onPressed: () {
                  addLedger(accountCtrl.text, double.parse(balanceCtrl.text));
                  accountCtrl.clear(); balanceCtrl.clear();
                },
                child: Text('Add Ledger'),
              )
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: ledgers.length,
            itemBuilder: (context, index) {
              var l = ledgers[index];
              return ListTile(
                title: Text(l['account']),
                subtitle: Text('Balance: ${l['balance']}'),
                trailing: IconButton(icon: Icon(Icons.delete), onPressed: () => deleteLedger(l['id'])),
              );
            },
          ),
        )
      ],
    );
  }
}
