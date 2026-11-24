/*
 * BackTrack helper script.
 *
 * 1. Mobile navigation drawer (Android).
 * 2. Sales overview chart on the dashboard (drawn from real revenue data).
 * 3. Point-of-sale cart on the "New sale" page.
 * 4. Progressive Web App service worker registration.
 *
 * The server still recalculates every sale amount and checks stock again,
 * so the numbers here are only to give the cashier instant feedback.
 */
(function () {
    "use strict";

    var currency = window.backtrackCurrency || "PHP";

    function money(value) {
        var amount = isNaN(value) ? 0 : Number(value);
        return currency + " " + amount.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }


    /* ============================================================
       1. Mobile navigation drawer
       ============================================================ */

    (function navigation() {
        var body = document.body;
        var menuButton = document.getElementById("menu-toggle");
        var moreButton = document.getElementById("more-toggle");
        var overlay = document.getElementById("sidebar-overlay");

        function open() {
            body.classList.add("nav-open");
        }

        function close() {
            body.classList.remove("nav-open");
        }

        if (menuButton) {
            menuButton.addEventListener("click", function () {
                body.classList.toggle("nav-open");
            });
        }

        if (moreButton) {
            moreButton.addEventListener("click", function () {
                body.classList.toggle("nav-open");
            });
        }

        if (overlay) {
            overlay.addEventListener("click", close);
        }

        // Close the drawer after tapping a link inside it.
        document.querySelectorAll(".sidebar .nav-item").forEach(function (link) {
            link.addEventListener("click", close);
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                close();
            }
        });
    })();


    /* ============================================================
       1b. Show / hide the password on the sign-in screen
       ============================================================ */

    (function passwordToggle() {
        var button = document.getElementById("toggle-password");
        var input = document.getElementById("password");
        if (!button || !input) {
            return;
        }

        var open = '<path d="M2.2 12S5.6 5.8 12 5.8 21.8 12 21.8 12 18.4 18.2 12 18.2 2.2 12 2.2 12Z"/>'
            + '<circle cx="12" cy="12" r="3.1"/>';
        var closed = '<path d="M3 4.5l18 15"/>'
            + '<path d="M9.9 5.2A9.9 9.9 0 0 1 12 5.8c6.4 0 9.8 6.2 9.8 6.2a17.6 17.6 0 0 1-3.4 4"/>'
            + '<path d="M6.4 7.1A17.2 17.2 0 0 0 2.2 12S5.6 18.2 12 18.2a9.6 9.6 0 0 0 3.4-.6"/>'
            + '<path d="M10 10a2.8 2.8 0 0 0 3.9 3.9"/>';

        button.addEventListener("click", function () {
            var show = input.type === "password";
            input.type = show ? "text" : "password";
            button.classList.toggle("is-visible", show);
            button.setAttribute("aria-label", show ? "Hide password" : "Show password");
            button.setAttribute("aria-pressed", show ? "true" : "false");
            button.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" '
                + 'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'
                + (show ? closed : open) + "</svg>";
        });
    })();


    /* ============================================================
       2. Sales overview chart
       ============================================================ */

    (function chart() {
        var host = document.getElementById("sales-chart");
        if (!host) {
            return;
        }

        var points = Array.prototype.map.call(
            host.querySelectorAll(".chart-points span"),
            function (span) {
                return {
                    label: span.getAttribute("data-label") || "",
                    date: span.getAttribute("data-date") || "",
                    value: parseFloat(span.getAttribute("data-value") || "0")
                };
            }
        );

        if (!points.length) {
            return;
        }

        var totalValue = points.reduce(function (sum, point) {
            return sum + point.value;
        }, 0);

        if (totalValue <= 0) {
            var empty = document.createElement("div");
            empty.className = "chart-empty";
            empty.innerHTML = "<strong>No sales in the last 7 days</strong>" +
                "<span>Record a sale and the chart will fill in.</span>";
            host.appendChild(empty);
            return;
        }

        var NS = "http://www.w3.org/2000/svg";
        var width = 720;
        var height = 240;
        var padLeft = 54;
        var padRight = 18;
        var padTop = 18;
        var padBottom = 32;

        var max = Math.max.apply(null, points.map(function (p) { return p.value; }));
        var step = max / 4;
        var niceMax = Math.ceil(max / step) * step;

        var plotWidth = width - padLeft - padRight;
        var plotHeight = height - padTop - padBottom;

        function x(index) {
            if (points.length === 1) {
                return padLeft + plotWidth / 2;
            }
            return padLeft + (plotWidth / (points.length - 1)) * index;
        }

        function y(value) {
            return padTop + plotHeight - (value / niceMax) * plotHeight;
        }

        function el(name, attrs, text) {
            var node = document.createElementNS(NS, name);
            Object.keys(attrs).forEach(function (key) {
                node.setAttribute(key, attrs[key]);
            });
            if (text !== undefined) {
                node.textContent = text;
            }
            return node;
        }

        var svg = el("svg", {
            viewBox: "0 0 " + width + " " + height,
            preserveAspectRatio: "none",
            role: "img",
            "aria-label": "Revenue for the last seven days"
        });

        // Horizontal grid lines and their labels.
        for (var line = 0; line <= 4; line++) {
            var value = niceMax * (line / 4);
            var lineY = y(value);

            svg.appendChild(el("line", {
                x1: padLeft, y1: lineY, x2: width - padRight, y2: lineY,
                stroke: "#e9eef7", "stroke-width": 1
            }));

            svg.appendChild(el("text", {
                x: padLeft - 10, y: lineY + 4, "text-anchor": "end",
                fill: "#8b9ab1", "font-size": "11"
            }, value >= 1000 ? Math.round(value / 1000) + "k" : Math.round(value)));
        }

        var linePath = points.map(function (point, index) {
            return (index === 0 ? "M" : "L") + x(index) + " " + y(point.value);
        }).join(" ");

        var areaPath = linePath +
            " L" + x(points.length - 1) + " " + y(0) +
            " L" + x(0) + " " + y(0) + " Z";

        var gradientId = "chart-fill";
        var defs = el("defs", {});
        var gradient = el("linearGradient", { id: gradientId, x1: "0", y1: "0", x2: "0", y2: "1" });
        gradient.appendChild(el("stop", { offset: "0%", "stop-color": "#1d6ef2", "stop-opacity": "0.22" }));
        gradient.appendChild(el("stop", { offset: "100%", "stop-color": "#1d6ef2", "stop-opacity": "0" }));
        defs.appendChild(gradient);
        svg.appendChild(defs);

        svg.appendChild(el("path", { d: areaPath, fill: "url(#" + gradientId + ")" }));
        svg.appendChild(el("path", {
            d: linePath, fill: "none", stroke: "#1d6ef2", "stroke-width": 3,
            "stroke-linejoin": "round", "stroke-linecap": "round"
        }));

        points.forEach(function (point, index) {
            var isLast = index === points.length - 1;
            svg.appendChild(el("circle", {
                cx: x(index), cy: y(point.value), r: isLast ? 6 : 4,
                fill: "#fff", stroke: "#1d6ef2", "stroke-width": 3
            }));

            svg.appendChild(el("text", {
                x: x(index), y: height - 10, "text-anchor": "middle",
                fill: "#6b7a90", "font-size": "11.5"
            }, point.label));

            if (isLast) {
                var label = money(point.value);
                var boxWidth = Math.max(76, label.length * 7.2);
                var boxX = Math.min(width - padRight - boxWidth, Math.max(padLeft, x(index) - boxWidth / 2));
                var boxY = Math.max(2, y(point.value) - 36);

                svg.appendChild(el("rect", {
                    x: boxX, y: boxY, width: boxWidth, height: 24, rx: 8,
                    fill: "#0b1730"
                }));
                svg.appendChild(el("text", {
                    x: boxX + boxWidth / 2, y: boxY + 16, "text-anchor": "middle",
                    fill: "#fff", "font-size": "12", "font-weight": "600"
                }, label));
            }
        });

        host.appendChild(svg);
    })();


    /* ============================================================
       3. Point of sale cart
       ============================================================ */

    (function pointOfSale() {
        var form = document.getElementById("sale-form");
        var linesContainer = document.getElementById("cart-lines");
        if (!form || !linesContainer) {
            return; // Not the sale page.
        }

        var template = document.getElementById("cart-line-template");
        var emptyMessage = document.getElementById("cart-empty");
        var grid = document.getElementById("product-grid");
        var gridEmpty = document.getElementById("grid-empty");
        var searchInput = document.getElementById("product-search");
        var discountInput = document.getElementById("discountAmount");
        var paidInput = document.getElementById("amountPaid");
        var clearButton = document.getElementById("clear-cart");
        var saveButton = document.getElementById("save-sale");

        var cart = {};      // productId -> { id, name, price, stock, quantity }
        var order = [];     // productIds in the order they were added
        var lineElements = {}; // productId -> DOM line

        function totalQuantity() {
            return order.reduce(function (sum, id) {
                return sum + cart[id].quantity;
            }, 0);
        }

        function renumber() {
            order.forEach(function (id, index) {
                var line = lineElements[id];
                line.querySelector(".line-product").name = "items[" + index + "].productId";
                line.querySelector(".line-qty").name = "items[" + index + "].quantity";
            });
        }

        function renderTotals() {
            var subtotal = order.reduce(function (sum, id) {
                return sum + cart[id].price * cart[id].quantity;
            }, 0);

            var discount = parseFloat(discountInput && discountInput.value) || 0;
            if (discount < 0) {
                discount = 0;
            }

            var total = Math.max(subtotal - discount, 0);
            var paid = parseFloat(paidInput && paidInput.value) || 0;
            var change = Math.max(paid - total, 0);

            setText("subtotal-display", money(subtotal));
            setText("discount-display", money(discount));
            setText("total-display", money(total));
            setText("change-display", money(change));
            setText("bar-total-display", money(total));
        }

        function setText(id, value) {
            var node = document.getElementById(id);
            if (node) {
                node.textContent = value;
            }
        }

        function addProduct(tile) {
            var id = tile.getAttribute("data-id");
            var stock = parseInt(tile.getAttribute("data-stock") || "0", 10);
            var price = parseFloat(tile.getAttribute("data-price") || "0");

            if (stock <= 0) {
                return;
            }

            if (cart[id]) {
                if (cart[id].quantity >= stock) {
                    alert("Only " + stock + " of " + cart[id].name + " in stock.");
                    return;
                }
                cart[id].quantity += 1;
                syncLine(cart[id]);
                renderTotals();
                return;
            }

            cart[id] = {
                id: id,
                name: tile.getAttribute("data-name") || "Product",
                price: price,
                stock: stock,
                quantity: 1
            };
            order.push(id);

            var fragment = template.content.cloneNode(true);
            var line = fragment.querySelector(".cart-line");

            line.querySelector(".line-product").value = id;
            line.querySelector(".line-name").textContent = cart[id].name;
            line.querySelector(".line-price").textContent = money(price) + " each";
            line.querySelector(".line-amount").textContent = money(price);

            var quantityInput = line.querySelector(".line-qty");
            quantityInput.value = "1";
            quantityInput.max = stock;

            line.querySelector(".qty-plus").addEventListener("click", function () {
                changeQuantity(id, 1);
            });

            line.querySelector(".qty-minus").addEventListener("click", function () {
                changeQuantity(id, -1);
            });

            quantityInput.addEventListener("input", function () {
                var value = parseInt(quantityInput.value, 10);
                if (isNaN(value) || value < 1) {
                    value = 1;
                }
                if (value > cart[id].stock) {
                    value = cart[id].stock;
                    alert("Only " + cart[id].stock + " of " + cart[id].name + " in stock.");
                }
                cart[id].quantity = value;
                quantityInput.value = value;
                syncLine(cart[id]);
                renderTotals();
            });

            quantityInput.addEventListener("blur", function () {
                quantityInput.value = cart[id].quantity;
            });

            line.querySelector(".remove-line").addEventListener("click", function () {
                removeProduct(id);
            });

            linesContainer.appendChild(fragment);
            lineElements[id] = linesContainer.lastElementChild;

            renumber();
            renderTotals();
            updateEmptyStates();
        }

        function changeQuantity(id, delta) {
            var item = cart[id];
            if (!item) {
                return;
            }

            var next = item.quantity + delta;

            if (next < 1) {
                removeProduct(id);
                return;
            }

            if (next > item.stock) {
                alert("Only " + item.stock + " of " + item.name + " in stock.");
                return;
            }

            item.quantity = next;
            syncLine(item);
            renderTotals();
        }

        function syncLine(item) {
            var line = lineElements[item.id];
            if (!line) {
                return;
            }
            var input = line.querySelector(".line-qty");
            if (document.activeElement !== input) {
                input.value = item.quantity;
            }
            line.querySelector(".line-amount").textContent = money(item.price * item.quantity);
        }

        function removeProduct(id) {
            var line = lineElements[id];
            if (line) {
                line.remove();
            }
            delete lineElements[id];
            delete cart[id];
            order = order.filter(function (value) {
                return value !== id;
            });
            renumber();
            renderTotals();
            updateEmptyStates();
        }

        function clearCart() {
            order.slice().forEach(removeProduct);
        }

        function updateEmptyStates() {
            if (emptyMessage) {
                emptyMessage.style.display = order.length ? "none" : "";
            }
        }

        if (grid) {
            grid.querySelectorAll(".product-tile").forEach(function (tile) {
                tile.addEventListener("click", function () {
                    addProduct(tile);
                });
            });
        }

        if (searchInput && grid) {
            searchInput.addEventListener("input", function () {
                var term = searchInput.value.trim().toLowerCase();
                var visible = 0;

                grid.querySelectorAll(".product-tile").forEach(function (tile) {
                    var text = (tile.getAttribute("data-name") + " " + tile.getAttribute("data-sku")).toLowerCase();
                    var matches = text.indexOf(term) !== -1;
                    tile.style.display = matches ? "" : "none";
                    if (matches) {
                        visible += 1;
                    }
                });

                if (gridEmpty) {
                    gridEmpty.hidden = visible > 0;
                }
            });
        }

        if (clearButton) {
            clearButton.addEventListener("click", clearCart);
        }

        if (discountInput) {
            discountInput.addEventListener("input", renderTotals);
        }

        if (paidInput) {
            paidInput.addEventListener("input", renderTotals);
        }

        form.addEventListener("submit", function (event) {
            if (!order.length) {
                event.preventDefault();
                alert("Add at least one product to the sale.");
                return;
            }

            var invalid = order.some(function (id) {
                return cart[id].quantity < 1 || cart[id].quantity > cart[id].stock;
            });

            if (invalid) {
                event.preventDefault();
                alert("Check the quantities: one product is above its available stock.");
                return;
            }

            if (saveButton) {
                saveButton.disabled = true;
                saveButton.textContent = "Saving…";
            }
        });

        renderTotals();
        updateEmptyStates();

        // Expose the quantity total for future use (and keep JSHint quiet).
        window.backtrackCartCount = totalQuantity;
    })();


    /* ============================================================
       4. Progressive Web App
       ============================================================ */

    // Only the real, standalone app installs the service worker. When the
    // page is shown inside a frame (preview panels, embeds) registering one
    // is pointless and can make the frame fail to load.
    if ("serviceWorker" in navigator && window.self === window.top) {
        window.addEventListener("load", function () {
            navigator.serviceWorker.register("/sw.js").catch(function () {
                // Installing the app is optional: ignore failures quietly.
            });
        });
    }
})();
